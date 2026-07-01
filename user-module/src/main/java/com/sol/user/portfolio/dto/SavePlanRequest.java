package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record SavePlanRequest(
        @NotNull PlanType planType,
        @Positive BigDecimal monthlyIncome,
        @DecimalMin("0") BigDecimal currentCoverageRate,
        @DecimalMin("0") BigDecimal totalCoverageRate,
        @PositiveOrZero BigDecimal currentMonthlyShortfall,
        @PositiveOrZero BigDecimal residualMonthlyShortfall,
        @Positive BigDecimal principalAmount,
        @Valid @NotEmpty List<HoldingItem> holdings
) {
    public record HoldingItem(
            @NotNull Long productId,
            String ticker,
            String productName,
            @DecimalMin("0") @DecimalMax("1") BigDecimal weight,
            @Positive BigDecimal targetAmount
    ) {}
}
