package com.sol.product.external.ls.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sol.product.etf.realtime.EtfPricePayload;
import com.sol.product.etf.realtime.websocket.EtfPriceWebSocketHandler;
import com.sol.product.etf.realtime.EtfRealtimeCache;
import com.sol.product.etf.realtime.EtfTickerWhitelist;
import com.sol.product.external.ls.LsProperties;
import com.sol.product.external.ls.dto.LsWsRequest;
import com.sol.product.external.ls.dto.LsWsStockResponse;
import com.sol.product.external.ls.service.LsTokenService;
import com.sol.product.stock.realtime.StockRealtimeCache;
import com.sol.product.stock.realtime.StockTickerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class LsWebSocketClient extends TextWebSocketHandler {

    private final LsProperties lsProperties;
    private final LsTokenService lsTokenService;
    private final EtfRealtimeCache etfRealtimeCache;
    private final EtfPriceWebSocketHandler etfPriceWebSocketHandler;
    private final StockRealtimeCache stockRealtimeCache;
    private final StockTickerRegistry stockTickerRegistry;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile WebSocketSession session;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void connect() {
        if (!connecting.compareAndSet(false, true)) {
            log.debug("LS WebSocket 연결 이미 진행 중 - 중복 요청 무시");
            return;
        }
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.execute(this, lsProperties.getWsUrl()).whenComplete((sess, ex) -> {
                connecting.set(false);
                if (ex != null) {
                    log.error("LS WebSocket 연결 실패", ex);
                    scheduleReconnect();
                } else {
                    this.session = sess;
                    log.info("LS WebSocket 연결 성공");
                    resubscribeAll();
                }
            });
        } catch (Exception e) {
            connecting.set(false);
            log.error("LS WebSocket 연결 오류", e);
            scheduleReconnect();
        }
    }

    public void reconnect() {
        lsTokenService.clearToken();
        WebSocketSession current = this.session;
        try {
            if (current != null && current.isOpen()) {
                current.close();
            } else {
                connect();
            }
        } catch (Exception e) {
            log.error("LS WebSocket 세션 종료 실패", e);
            connect();
        }
    }

    private void scheduleReconnect() {
        log.info("LS WebSocket 5초 후 재연결 시도");
        reconnectScheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    private void resubscribeAll() {
        if (subscribedCodes.isEmpty()) return;
        log.info("LS WebSocket 재구독: {}개 종목", subscribedCodes.size());
        String token = lsTokenService.getToken();
        for (String code : subscribedCodes) {
            sendMessage(LsWsRequest.subscribe(token, code));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("LS WebSocket 재구독 완료");
    }

    public void subscribe(String ticker) {
        if (subscribedCodes.contains(ticker)) return;
        String token = lsTokenService.getToken();
        sendMessage(LsWsRequest.subscribe(token, ticker));
        subscribedCodes.add(ticker);
        log.debug("ETF 구독: {}", ticker);
    }

    public void unsubscribe(String ticker) {
        if (!subscribedCodes.contains(ticker)) return;
        String token = lsTokenService.getToken();
        sendMessage(LsWsRequest.unsubscribe(token, ticker));
        subscribedCodes.remove(ticker);
        log.debug("ETF 구독 해제: {}", ticker);
    }

    @Scheduled(fixedDelay = 60000)
    public void sendKeepAlive() {
        WebSocketSession current = this.session;
        if (current == null || !current.isOpen()) {
            log.warn("LS WebSocket keepalive: 세션 없음 - 재연결 시도");
            connect();
            return;
        }
        try {
            current.sendMessage(new PingMessage());
        } catch (IOException e) {
            log.warn("LS WebSocket keepalive 실패 - 재연결 시도: {}", e.getMessage());
            this.session = null;
            scheduleReconnect();
        }
    }

    private void sendMessage(LsWsRequest request) {
        WebSocketSession current = this.session;
        try {
            if (current == null || !current.isOpen()) {
                log.debug("LS WebSocket 세션 없음 - 재연결 후 재구독 예정");
                return;
            }
            String json = objectMapper.writeValueAsString(request);
            current.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("LS WebSocket 메시지 전송 실패", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode node = objectMapper.readTree(payload);
            JsonNode headerNode = node.get("header");
            if (headerNode == null || node.get("body") == null || node.get("body").isNull()) return;

            String trCd = headerNode.path("tr_cd").asText();

            if ("US3".equals(trCd)) {
                LsWsStockResponse response = objectMapper.treeToValue(node, LsWsStockResponse.class);
                if (response.body() == null) return;
                String ticker = response.body().shcode().trim();
                String price = response.body().price();
                String change = response.body().change();
                String drate = response.body().drate();
                String sign = response.body().sign();
                if (EtfTickerWhitelist.contains(ticker)) {
                    etfRealtimeCache.save(ticker, price, change, drate, sign);
                    EtfPricePayload pricePayload = EtfPricePayload.of(ticker, price, change, drate, sign);
                    if (pricePayload != null) {
                        etfPriceWebSocketHandler.broadcast(pricePayload);
                    }
                } else if (stockTickerRegistry.contains(ticker)) {
                    stockRealtimeCache.save(ticker, price, change, drate, sign);
                }
            }
        } catch (Exception e) {
            log.debug("LS WebSocket 메시지 파싱 실패: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("LS WebSocket 연결 종료: {}", status);
        this.session = null;
        lsTokenService.clearToken();
        scheduleReconnect();
    }
}
