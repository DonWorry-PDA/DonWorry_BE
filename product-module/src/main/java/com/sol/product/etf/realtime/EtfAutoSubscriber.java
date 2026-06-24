package com.sol.product.etf.realtime;

import com.sol.product.external.ls.websocket.LsWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtfAutoSubscriber {

    private final LsWebSocketClient lsWebSocketClient;

    @EventListener(ApplicationReadyEvent.class)
    public void autoSubscribe() {
        log.info("ETF 실시간 구독 시작: {}개", EtfTickerWhitelist.TICKERS.size());
        CompletableFuture.runAsync(() -> {
            for (String ticker : EtfTickerWhitelist.TICKERS) {
                lsWebSocketClient.subscribe(ticker);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log.info("ETF 실시간 구독 등록 완료");
        });
    }
}
