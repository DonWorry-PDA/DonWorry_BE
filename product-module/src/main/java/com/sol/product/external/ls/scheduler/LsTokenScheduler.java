package com.sol.product.external.ls.scheduler;

import com.sol.product.external.ls.websocket.LsWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LsTokenScheduler {

    private final LsWebSocketClient lsWebSocketClient;

    // 매일 06:55에 토큰 만료 전 WebSocket 재연결 (토큰 갱신 포함)
    @Scheduled(cron = "0 55 6 * * *")
    public void refreshToken() {
        log.info("LS 토큰 만료 예정 - WebSocket 재연결 시작");
        lsWebSocketClient.reconnect();
    }
}
