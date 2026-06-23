package com.sol.user.pension.dto;

import lombok.Builder;

@Builder
public record PensionDeferComparisonRow(
    int deferRate,
    long duringDeferMonthly,
    long afterDeferMonthly,
    long monthlyIncrease,
    Long breakEvenMonths,
    int coverageRateDuring,
    int coverageRateAfter,
    String stabilityDuring,
    String stabilityAfter
) {}
