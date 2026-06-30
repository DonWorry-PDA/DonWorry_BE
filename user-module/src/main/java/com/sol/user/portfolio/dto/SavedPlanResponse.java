package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SavedPlanResponse(
        PlanType planType,
        BigDecimal monthlyIncome,
        BigDecimal currentCoverageRate,
        BigDecimal totalCoverageRate,
        BigDecimal currentMonthlyShortfall,
        BigDecimal residualMonthlyShortfall,
        BigDecimal principalAmount,
        List<HoldingItem> holdings,
        LocalDateTime savedAt
) {
    public record HoldingItem(
            Long productId,
            String ticker,
            String productName,
            BigDecimal weight,
            BigDecimal targetAmount
    ) {}
}
