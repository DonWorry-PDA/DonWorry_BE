package com.sol.user.portfolio.dto;

import java.math.BigDecimal;

/**
 * Q3 트레이드오프 표의 한 행: 소진비율 선택지별 (월수령, 상속) 동시 제시.
 * 대표안(안정안) 기준으로 계산한다.
 */
public record Q3Scenario(
        int q3,                       // 0=상속우선 / 1=반반 / 2=소비우선
        BigDecimal monthlyIncome,
        BigDecimal inheritanceAmount
) {
}
