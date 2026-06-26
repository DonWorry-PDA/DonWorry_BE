package com.sol.user.asset.infra.rest;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DepositDetailClient {

    private final RestClient productRestClient;

    public DepositDetailClient(RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public Map<Long, DepositDetailItem> fetchDepositDetails(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        try {
            DepositDetailApiResponse response = productRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/product/products/deposit-details")
                            .queryParam("productIds", productIds)
                            .build())
                    .retrieve()
                    .body(DepositDetailApiResponse.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data().stream()
                    .collect(Collectors.toMap(DepositDetailItem::productId, item -> item));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
    }

    public Optional<Long> fetchFirstDepositProductId() {
        try {
            DepositDetailIdsResponse response = productRestClient.get()
                    .uri("/api/product/products/deposit-ids")
                    .retrieve()
                    .body(DepositDetailIdsResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.data().get(0));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
    }
}
