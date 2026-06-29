package com.sol.product.stock.realtime;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.stock.entity.StockDetail;
import com.sol.product.stock.repository.StockDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockPriceService {

    private final StockDetailRepository stockDetailRepository;
    private final StockRealtimeCache stockRealtimeCache;
    private final DailyPriceRepository dailyPriceRepository;

    public long getCurrentPriceByProductId(Long productId) {
        StockDetail stock = stockDetailRepository.findByProductProductId(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));

        Map<String, String> raw = stockRealtimeCache.getRaw(stock.getTickerCode());
        if (!raw.isEmpty() && !isBlank(raw.get("price"))) {
            return Long.parseLong(raw.get("price").trim());
        }

        return dailyPriceRepository.findTopByProductProductIdOrderByPriceDateDesc(productId)
                .map(dp -> {
                    try {
                        return dp.getClosingPrice().longValueExact();
                    } catch (ArithmeticException e) {
                        throw new BaseException(ErrorCode.PRICE_UNAVAILABLE);
                    }
                })
                .orElseThrow(() -> new BaseException(ErrorCode.PRICE_UNAVAILABLE));
    }

    /** productId 목록 → 현재가 Map. Redis 캐시 우선, 없으면 daily_price 폴백. 조회 실패 종목은 0. */
    public Map<Long, Long> getBatchPrices(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Map.of();

        Map<Long, StockDetail> byProductId = stockDetailRepository
                .findAllByProductProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        d -> d.getProduct().getProductId(),
                        d -> d
                ));

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            StockDetail detail = byProductId.get(productId);
            if (detail == null) {
                result.put(productId, 0L);
                continue;
            }
            result.put(productId, resolvePrice(detail, productId));
        }
        return result;
    }

    private long resolvePrice(StockDetail detail, Long productId) {
        Map<String, String> raw = stockRealtimeCache.getRaw(detail.getTickerCode());
        if (!raw.isEmpty() && !isBlank(raw.get("price"))) {
            try {
                return Long.parseLong(raw.get("price").trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return dailyPriceRepository.findTopByProductProductIdOrderByPriceDateDesc(productId)
                .map(dp -> {
                    try {
                        return dp.getClosingPrice().longValueExact();
                    } catch (ArithmeticException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
