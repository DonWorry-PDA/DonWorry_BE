package com.sol.user.portfolio.infra.rest;

public record ProductBatchItem(
        Long productId,
        String productName,
        String productType,
        String tickerCode
) {
}
