package com.sol.user.onboarding.dto;

import java.math.BigDecimal;

public record OnboardingResponse(
        Long userId,
        Integer age,
        Boolean retired,
        Boolean nationalPensionReceiving,
        BigDecimal monthlyTargetLivingCost,
        BigDecimal monthlyExpectedMedicalCost,
        boolean onboardingCompleted
) {
}
