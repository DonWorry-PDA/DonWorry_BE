package com.sol.user.portfolio.type;

/**
 * 자산 버킷 역할. 공시 위험등급이 아닌 수동 매핑으로 결정한다(STEP5-0).
 * 안전: 만기보유 원금보존 성격(예금·국고채·회사채·MMF·채권혼합 등)
 * 위험: 주식형 ETF(지수·배당·성장)
 * 단기: 유동성안 선확보분(원금변동 없는 현금성) — 같은 현금성 상품이라도 안전버킷이 아닌 단기버킷에 담길 때의 위치 구분.
 *
 * <p>SAFE/RISK는 ticker 분류({@code roleOf})로 결정되지만, SHORT_TERM은 ticker 속성이 아니라
 * 단기버킷에 배치된 위치로 부여된다(같은 CD금리MMF가 안전버킷이면 SAFE, 단기버킷이면 SHORT_TERM).
 */
public enum BucketRole {
    SAFE,
    RISK,
    SHORT_TERM
}
