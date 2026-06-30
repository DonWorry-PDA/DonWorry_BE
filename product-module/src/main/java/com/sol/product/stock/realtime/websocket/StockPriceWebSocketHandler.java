package com.sol.product.stock.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sol.product.stock.realtime.StockPricePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 프론트 WebSocket 연결 관리 및 실시간 개별주식 가격 broadcast.
 * broadcast는 별도 스레드에서 실행해 LS 수신 스레드를 블로킹하지 않는다. (ETF 핸들러와 대칭)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ExecutorService broadcastExecutor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("프론트 주식 WebSocket 연결: {} (총 {}명)", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("프론트 주식 WebSocket 종료: {} (총 {}명)", session.getId(), sessions.size());
    }

    public void broadcast(StockPricePayload payload) {
        if (sessions.isEmpty()) return;
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
            for (WebSocketSession session : sessions) {
                broadcastExecutor.submit(() -> send(session, message));
            }
        } catch (Exception e) {
            log.error("주식 가격 broadcast 직렬화 실패: {}", payload.ticker(), e);
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("프론트 주식 WebSocket 전송 실패: {}", session.getId());
                sessions.remove(session);
            }
        }
    }
}
