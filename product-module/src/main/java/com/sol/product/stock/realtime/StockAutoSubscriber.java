package com.sol.product.stock.realtime;

import com.sol.product.external.ls.websocket.LsWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockAutoSubscriber {

    private final LsWebSocketClient lsWebSocketClient;
    private final StockTickerRegistry stockTickerRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void subscribeAll() {
        Set<String> tickers = Set.of("000660", "005380", "005930", "035420", "035720", "373220");
        if (tickers.isEmpty()) {
            log.info("구독할 주식 티커 없음 - 건너뜀");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // WebSocket 연결 안정화 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int count = 0;
            for (String ticker : tickers) {
                lsWebSocketClient.subscribe(ticker);
                count++;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("주식 실시간 구독 완료: {}개", count);
        });
    }
}
