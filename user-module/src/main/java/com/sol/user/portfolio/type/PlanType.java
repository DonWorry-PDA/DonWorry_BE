package com.sol.user.portfolio.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포트폴리오 3안.
 */
@Getter
@RequiredArgsConstructor
public enum PlanType {
    STABLE("안정안", "매달 가장 일정하게, 변동 최소"),
    BALANCED("균형안", "수령액 비슷하나 일부를 성장자산에 둬 장기 자산증가 여지(변동 있음)"),
    LIQUIDITY("유동성안", "단기 목돈 따로 확보 후 나머지 안정 운용");

    private final String label;
    private final String description;
}
