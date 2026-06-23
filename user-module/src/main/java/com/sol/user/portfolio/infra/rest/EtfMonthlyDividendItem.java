package com.sol.user.portfolio.infra.rest;

import java.math.BigDecimal;

public record EtfMonthlyDividendItem(
        Long productId,
        BigDecimal monthlyDividendPerUnit
) {
}
