package com.sol.user.retirement.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record RetirementSimParamsResponse(
        Integer ageYears,
        BigDecimal totalAssetsKrw,
        BigDecimal monthlyLivingKrw,
        BigDecimal monthlyPensionKrw
) {
}
