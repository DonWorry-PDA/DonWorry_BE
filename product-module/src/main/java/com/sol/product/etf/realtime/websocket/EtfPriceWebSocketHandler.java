package com.sol.product.etf.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sol.product.etf.realtime.EtfPricePayload;
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

/**
 * 프론트 WebSocket 연결 관리 및 실시간 ETF 가격 broadcast.
 * 연결된 모든 클라이언트에게 가격 업데이트를 push한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("프론트 WebSocket 연결: {} (총 {}명)", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("프론트 WebSocket 종료: {} (총 {}명)", session.getId(), sessions.size());
    }

    public void broadcast(EtfPricePayload payload) {
        if (sessions.isEmpty()) return;
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            log.warn("프론트 WebSocket 전송 실패: {}", session.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("ETF 가격 broadcast 실패: {}", payload.ticker(), e);
        }
    }
}
