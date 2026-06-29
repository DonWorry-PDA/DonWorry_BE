package com.sol.product.stock.realtime;

import com.sol.product.stock.repository.StockDetailRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockTickerRegistry {

    private final StockDetailRepository stockDetailRepository;
    private volatile Set<String> tickerSet = Set.of();

    @PostConstruct
    public void load() {
        try {
            tickerSet = Set.copyOf(
                    stockDetailRepository.findAllTickerCodes().stream()
                            .filter(t -> t != null && !t.isBlank())
                            .collect(Collectors.toSet())
            );
            log.info("주식 티커 로드: {}개", tickerSet.size());
        } catch (Exception e) {
            log.warn("주식 티커 로드 실패 (DB 미준비 등): {}", e.getMessage());
        }
    }

    public boolean contains(String ticker) {
        return tickerSet.contains(ticker);
    }

    public Set<String> getAll() {
        return tickerSet;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void reloadIfEmpty() {
        if (tickerSet.isEmpty()) {
            log.info("주식 티커 미적재 상태 - 재시도");
            load();
        }
    }
}
