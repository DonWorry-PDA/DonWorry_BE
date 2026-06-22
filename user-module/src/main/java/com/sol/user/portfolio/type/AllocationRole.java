package com.sol.user.portfolio.type;

/**
 * 화면 배분 항목의 역할 구분. 색상/그룹핑을 인덱스가 아닌 의미 기준으로 잡도록 FE에 제공한다.
 * SAFE: 안전버킷(바닥자산 포함) / RISK: 위험버킷 ETF 개별 / SHORT_TERM: 단기 유동성(유동성안만)
 */
public enum AllocationRole {
    SAFE,
    RISK,
    SHORT_TERM
}
