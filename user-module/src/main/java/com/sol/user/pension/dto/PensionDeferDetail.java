package com.sol.user.pension.dto;

import lombok.Builder;

@Builder
public record PensionDeferDetail(
    int deferRate,
    int deferYears,
    long basePensionMonthly,
    long duringDeferMonthly,
    long afterDeferMonthly,
    long monthlyIncrease,
    Long breakEvenMonths,
    int coverageRateBefore,
    int coverageRateAfter,
    String insight
) {}
