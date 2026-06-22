package com.sol.user.portfolio.type;

/**
 * 안별 위험자산 코어 슬롯(의미 단위). 실제 ticker는 PropensityTier별로 해소된다.
 * - HEDGED_CORE  : 환헤지 배당 코어 (적극·공격=배당다우존스(H), 위험중립=환노출로 강등)
 * - UNHEDGED_CORE: 환노출 배당 코어 (배당다우존스)
 * - GROWTH       : 성장 (나스닥100)
 */
public enum CoreSlot {
    HEDGED_CORE,
    UNHEDGED_CORE,
    GROWTH
}
