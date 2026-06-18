package com.sol.user.stability.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record LifeStabilityMetrics(
        BigDecimal cashflowCoverageRate,
        BigDecimal essentialExpenseRate,
        BigDecimal medicalPreparednessMonths,
        BigDecimal liquidityMonths,
        BigDecimal debtBurdenRate,
        BigDecimal riskAssetDependencyRate
) {
}
