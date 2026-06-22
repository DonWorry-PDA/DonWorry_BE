package com.sol.user.portfolio.type;

/**
 * 안 카드의 화면 표시 상태. 안별 α충족률을 교차 비교해 결정한다(STEP6 이후, Mapper에서 파생).
 * RECOMMENDED는 NORMAL 트랙에서 최고 충족률 안 1개에만 부여(동점 시 STABLE 우선).
 */
public enum PlanStatus {
    RECOMMENDED,
    AVAILABLE
}
