package com.sol.product.etf.realtime;

import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtfClosingPriceScheduler {

    private final EtfRealtimeCache etfRealtimeCache;
    private final EtfDetailRepository etfDetailRepository;
    private final DailyPriceRepository dailyPriceRepository;

    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void saveClosingPrices() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("ETF 종가 저장 시작: {}", today);

        Map<String, EtfDetail> etfByTicker = etfDetailRepository
                .findAllByTickerCodeIn(EtfTickerWhitelist.TICKERS).stream()
                .collect(Collectors.toMap(EtfDetail::getTickerCode, Function.identity()));

        int saved = 0;
        for (String ticker : EtfTickerWhitelist.TICKERS) {
            Map<String, String> raw = etfRealtimeCache.getRaw(ticker);
            String price = raw.get("price");
            String change = raw.get("change");
            String drate = raw.get("drate");
            if (isBlank(price) || isBlank(change) || isBlank(drate)) {
                log.warn("ETF 종가 Redis 캐시 불완전: {}", ticker);
                continue;
            }

            EtfDetail etfDetail = etfByTicker.get(ticker);
            if (etfDetail == null) {
                log.warn("ETF 상세 없음: {}", ticker);
                continue;
            }

            Long productId = etfDetail.getProduct().getProductId();

            dailyPriceRepository.deleteAllByProductProductId(productId);

            dailyPriceRepository.save(DailyPrice.builder()
                    .product(etfDetail.getProduct())
                    .priceDate(today)
                    .closingPrice(new BigDecimal(price))
                    .priceChange(new BigDecimal(change))
                    .changeRate(new BigDecimal(drate))
                    .build());

            saved++;
        }

        log.info("ETF 종가 저장 완료: {}건 / {}개 티커", saved, EtfTickerWhitelist.TICKERS.size());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
