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

    // 매일 06:55 KST에 토큰 만료 전 WebSocket 재연결 (재연결 시 토큰 자동 갱신)
    @Scheduled(cron = "0 55 6 * * *", zone = "Asia/Seoul")
    public void reconnectForTokenRenewal() {
        log.info("LS 토큰 만료 예정 - WebSocket 재연결 시작");
        lsWebSocketClient.reconnect();
    }
}
