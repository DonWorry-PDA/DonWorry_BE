package com.sol.user.portfolio.type;

/**
 * 환 전략. etf_detail에 currency 컬럼이 없어 ticker 기반으로 판정한다((H) 포함=HEDGED).
 */
public enum CurrencyExposure {
    UNHEDGED,
    HEDGED
}
