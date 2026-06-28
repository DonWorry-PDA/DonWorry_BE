package com.sol.product.product.dto;

import com.sol.product.product.entity.FinancialProduct;

public record ProductBatchItem(
        Long productId,
        String productName,
        String productType,
        String tickerCode
) {
    public static ProductBatchItem from(FinancialProduct product) {
        return new ProductBatchItem(
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                null
        );
    }
}
