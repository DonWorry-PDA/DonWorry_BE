package com.sol.user.stability.dto;

import lombok.Builder;

@Builder
public record LifeStabilityIndicators(
        String cashflowStatus,
        String essentialExpenseStatus,
        String medicalPreparednessStatus,
        String liquidityStatus,
        String debtBurdenStatus,
        String riskAssetDependencyStatus
) {
}
