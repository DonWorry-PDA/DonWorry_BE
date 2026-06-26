package com.sol.user.asset.infra.rest;

import java.math.BigDecimal;

public record DepositDetailItem(
        Long productId,
        BigDecimal interestRate,
        Integer maturityMonths
) {}
