package com.sol.product.stock.realtime;

import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.stock.entity.StockDetail;
import com.sol.product.stock.repository.StockDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockClosingPriceScheduler {

    private final StockTickerRegistry stockTickerRegistry;
    private final StockDetailRepository stockDetailRepository;
    private final StockRealtimeCache stockRealtimeCache;
    private final DailyPriceRepository dailyPriceRepository;

    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void saveClosingPrices() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("주식 종가 저장 시작: {}", today);

        Set<String> tickers = stockTickerRegistry.getAll();
        if (tickers.isEmpty()) return;

        Map<String, StockDetail> byTicker = stockDetailRepository
                .findAllByTickerCodeIn(List.copyOf(tickers)).stream()
                .collect(Collectors.toMap(StockDetail::getTickerCode, Function.identity()));

        int saved = 0;
        for (String ticker : tickers) {
            StockDetail detail = byTicker.get(ticker);
            if (detail == null) continue;

            Map<String, String> raw = stockRealtimeCache.getRaw(ticker);
            String price = raw.get("price");
            String change = raw.get("change");
            String drate = raw.get("drate");
            if (isBlank(price) || isBlank(change) || isBlank(drate)) {
                continue;
            }

            try {
                Long productId = detail.getProduct().getProductId();
                dailyPriceRepository.deleteAllByProductProductId(productId);
                dailyPriceRepository.save(DailyPrice.builder()
                        .product(detail.getProduct())
                        .priceDate(today)
                        .closingPrice(new BigDecimal(price))
                        .priceChange(new BigDecimal(change))
                        .changeRate(new BigDecimal(drate))
                        .build());
                saved++;
            } catch (Exception e) {
                log.warn("주식 종가 저장 실패 [{}]: {}", ticker, e.getMessage());
            }
        }
        log.info("주식 종가 저장 완료: {}건 / {}개 티커", saved, tickers.size());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
