package com.sol.user.portfolio.infra.rest;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
}
