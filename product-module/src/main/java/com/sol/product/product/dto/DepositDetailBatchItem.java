package com.sol.product.product.dto;

import java.math.BigDecimal;

public record DepositDetailBatchItem(
        Long productId,
        String productName,
        BigDecimal interestRate,
        Integer maturityMonths
) {}
