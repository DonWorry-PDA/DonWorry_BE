package com.sol.user.portfolio.type;

/**
 * 자산 버킷 역할. 공시 위험등급이 아닌 수동 매핑으로 결정한다(STEP5-0).
 * 안전: 만기보유 원금보존 성격(예금·국고채·회사채·MMF·채권혼합 등)
 * 위험: 주식형 ETF(지수·배당·성장)
 */
public enum BucketRole {
    SAFE,
    RISK
}
