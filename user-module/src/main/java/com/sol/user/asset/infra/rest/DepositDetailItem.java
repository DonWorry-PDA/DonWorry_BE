package com.sol.user.asset.infra.rest;

import java.math.BigDecimal;

public record DepositDetailItem(
        Long productId,
        String productName,
        BigDecimal interestRate,
        Integer maturityMonths
) {}
