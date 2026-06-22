package com.sol.user.portfolio.type;

/**
 * 코어 슬롯 해소를 위한 성향 그룹.
 * - ACTIVE_PLUS : 적극·공격투자형(2~6 권유) — 2등급 상품(환헤지 코어·나스닥100) 권유 가능
 * - NEUTRAL     : 위험중립형(3~6 권유) — 2등급 권유 불가 → 환헤지 코어를 환노출로 강등, 균형안 미제공
 *
 * ※ 안정형·안정추구형은 주식 비권유층이라 사실상 미발생. 도달하더라도 권유가능등급 필터가
 *   위험 ETF(2~3등급)를 전부 제거하므로 NEUTRAL로 묶어도 무방하다.
 */
public enum PropensityTier {
    ACTIVE_PLUS,
    NEUTRAL;

    public static PropensityTier from(InvestmentPropensity propensity) {
        // gradeLimit: 공격5·적극4·위험중립3·안정추구2·안정1 → 4 이상이면 2등급 권유 가능
        return propensity.getGradeLimit() >= 4 ? ACTIVE_PLUS : NEUTRAL;
    }
}
