package com.sol.user.onboarding.dto;

import java.math.BigDecimal;

/**
 * 온보딩 입력. 마이데이터로는 알 수 없는 사용자 직접 입력값을 받는다.
 * 목표 생활비/예상 의료비는 생활 안정도 계산에 필요하며, 미입력 시 시연 기본값을 사용한다.
 */
public record OnboardingRequest(
        Integer age,
        Boolean retired,
        Boolean nationalPensionReceiving,
        BigDecimal monthlyTargetLivingCost,
        BigDecimal monthlyExpectedMedicalCost,
        Boolean thirdPartyAgreed,
        Boolean marketingAgreed
) {
}
