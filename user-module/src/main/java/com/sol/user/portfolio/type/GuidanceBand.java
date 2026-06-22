package com.sol.user.portfolio.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * α 충족률 안내 구간. 안별 α충족률 중 최댓값(달성 가능 최선) 기준으로 결정한다.
 */
@Getter
@RequiredArgsConstructor
public enum GuidanceBand {
    SUFFICIENT("운용으로 충분/초과"),       // α ≥ 100
    NEAR("목표 근접"),                      // 90 ~ 100
    TRADEOFF("안정·균형 트레이드오프"),       // 70 ~ 90
    HARD("운용으로도 어려움");               // < 70

    private final String description;

    public static GuidanceBand from(BigDecimal maxCoverageRate) {
        if (maxCoverageRate.compareTo(BigDecimal.valueOf(100)) >= 0) return SUFFICIENT;
        if (maxCoverageRate.compareTo(BigDecimal.valueOf(90)) >= 0) return NEAR;
        if (maxCoverageRate.compareTo(BigDecimal.valueOf(70)) >= 0) return TRADEOFF;
        return HARD;
    }
}
