package com.sol.user.portfolio.infra.rest;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProductBatchClient {

    private final RestClient productRestClient;

    public ProductBatchClient(RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public Map<Long, ProductBatchItem> fetchProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        try {
            ProductBatchApiResponse response = productRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/product/products/batch")
                            .queryParam("ids", productIds)
                            .build())
                    .retrieve()
                    .body(ProductBatchApiResponse.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data().stream()
                    .collect(Collectors.toMap(ProductBatchItem::productId, item -> item));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
    }

    public Map<Long, BigDecimal> fetchEtfMonthlyDividends(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        try {
            EtfMonthlyDividendApiResponse response = productRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/product/etfs/monthly-dividends")
                            .queryParam("productIds", productIds)
                            .build())
                    .retrieve()
                    .body(EtfMonthlyDividendApiResponse.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data().stream()
                    .collect(Collectors.toMap(
                            EtfMonthlyDividendItem::productId,
                            EtfMonthlyDividendItem::monthlyDividendPerUnit
                    ));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
    }

    /** productId 목록 → 주식 현재가 Map(원). Redis 우선·daily_price 폴백은 product-module이 처리. 실패 시 빈 Map(주식 평가액 0으로 처리). */
    public Map<Long, Long> fetchStockPrices(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        try {
            StockPriceApiResponse response = productRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/product/stocks/prices")
                            .queryParam("productIds", productIds)
                            .build())
                    .retrieve()
                    .body(StockPriceApiResponse.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data();
        } catch (Exception e) {
            log.warn("주식 현재가 조회 실패 - 개별주 평가액 0 처리: {}", e.getMessage());
            return Map.of();
        }
    }
}
