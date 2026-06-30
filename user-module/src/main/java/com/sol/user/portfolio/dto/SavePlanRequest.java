package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SavePlanRequest(
        @NotNull PlanType planType,
        BigDecimal monthlyIncome,
        BigDecimal currentCoverageRate,
        BigDecimal totalCoverageRate,
        BigDecimal currentMonthlyShortfall,
        BigDecimal residualMonthlyShortfall,
        BigDecimal principalAmount,
        @NotEmpty List<HoldingItem> holdings
) {
    public record HoldingItem(
            @NotNull Long productId,
            String ticker,
            String productName,
            BigDecimal weight,
            BigDecimal targetAmount
    ) {}
}
