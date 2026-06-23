package com.sol.product.etf.dto;

import java.math.BigDecimal;

public record EtfMonthlyDividendItem(
        Long productId,
        BigDecimal monthlyDividendPerUnit
) {
}
