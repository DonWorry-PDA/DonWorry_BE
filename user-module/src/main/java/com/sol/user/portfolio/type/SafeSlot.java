package com.sol.user.portfolio.type;

/**
 * 안전버킷 코어 슬롯(의미 단위). 실제 ticker는 {@code SAFE_SLOT_TICKER}로 해소된다.
 * 위험버킷의 {@link CoreSlot}과 달리 tier 분기가 없다 — 안전버킷은 전 성향 적합(등급≥5)으로만 구성하기 때문.
 * - GOV     : 국고채(단기) — 원금안정 코어
 * - CREDIT  : 종합채권(AA-이상) — 약간의 스프레드 수익
 * - CASH_EQ : CD금리MMF — 원금변동 없는 예금 대체(단기버킷 겸용)
 */
public enum SafeSlot {
    GOV,
    CREDIT,
    CASH_EQ
}
