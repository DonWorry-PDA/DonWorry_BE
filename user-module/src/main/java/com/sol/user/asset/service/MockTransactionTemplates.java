package com.sol.user.asset.service;

import java.util.List;

class MockTransactionTemplates {

    record TransactionTemplate(String eventType, String title, long baseAmount) {}

    static final List<TransactionTemplate> NEED_IMPROVEMENT = List.of(
            new TransactionTemplate("CARD",      "이마트 에브리데이",  85_000),
            new TransactionTemplate("CARD",      "롯데마트",           95_000),
            new TransactionTemplate("CARD",      "홈플러스",           78_000),
            new TransactionTemplate("CARD",      "다이소",              6_500),
            new TransactionTemplate("CARD",      "GS25",                3_500),
            new TransactionTemplate("CARD",      "CU편의점",            4_000),
            new TransactionTemplate("CARD",      "이디야커피",          4_500),
            new TransactionTemplate("CARD",      "맥도날드",            9_500),
            new TransactionTemplate("CARD",      "배달의민족",         42_000),
            new TransactionTemplate("CARD",      "쿠팡",               95_000),
            new TransactionTemplate("CARD",      "올리브영",           18_000),
            new TransactionTemplate("CARD",      "세탁소",             15_000),
            new TransactionTemplate("TRANSPORT", "교통카드 충전",      65_000),
            new TransactionTemplate("UTILITY",   "한국전력",           52_000),
            new TransactionTemplate("UTILITY",   "도시가스",           42_000),
            new TransactionTemplate("UTILITY",   "수도요금",           18_000),
            new TransactionTemplate("PHONE",     "SKT 통신비",         55_000),
            new TransactionTemplate("MEDICAL",   "동네약국",            8_500),
            new TransactionTemplate("MEDICAL",   "내과의원",           15_000)
    );

    static final List<TransactionTemplate> NEED_COMPLEMENT = List.of(
            new TransactionTemplate("CARD",      "이마트",             98_000),
            new TransactionTemplate("CARD",      "홈플러스",           85_000),
            new TransactionTemplate("CARD",      "다이소",              9_000),
            new TransactionTemplate("CARD",      "스타벅스",           12_500),
            new TransactionTemplate("CARD",      "GS25",                5_500),
            new TransactionTemplate("CARD",      "올리브영",           32_000),
            new TransactionTemplate("CARD",      "CGV",                25_000),
            new TransactionTemplate("CARD",      "배달의민족",         38_000),
            new TransactionTemplate("CARD",      "쿠팡",              125_000),
            new TransactionTemplate("CARD",      "마켓컬리",           65_000),
            new TransactionTemplate("CARD",      "세탁소",             18_000),
            new TransactionTemplate("CARD",      "헤어샵",             35_000),
            new TransactionTemplate("TRANSPORT", "교통카드 충전",      65_000),
            new TransactionTemplate("TRANSPORT", "택시비",             18_000),
            new TransactionTemplate("UTILITY",   "한국전력",           58_000),
            new TransactionTemplate("UTILITY",   "도시가스",           45_000),
            new TransactionTemplate("UTILITY",   "수도요금",           22_000),
            new TransactionTemplate("PHONE",     "KT 통신비",          60_000),
            new TransactionTemplate("MEDICAL",   "정형외과",           25_000),
            new TransactionTemplate("MEDICAL",   "약국",               12_000)
    );

    static final List<TransactionTemplate> STABLE = List.of(
            new TransactionTemplate("CARD",      "SSG닷컴",           185_000),
            new TransactionTemplate("CARD",      "현대백화점 식품관", 125_000),
            new TransactionTemplate("CARD",      "이마트",            115_000),
            new TransactionTemplate("CARD",      "스타벅스",           18_500),
            new TransactionTemplate("CARD",      "올리브영",           55_000),
            new TransactionTemplate("CARD",      "헬스장 월회비",      85_000),
            new TransactionTemplate("CARD",      "레스토랑",           65_000),
            new TransactionTemplate("CARD",      "쿠팡",              145_000),
            new TransactionTemplate("CARD",      "마켓컬리",          110_000),
            new TransactionTemplate("CARD",      "GS샵",               75_000),
            new TransactionTemplate("CARD",      "서점",               32_000),
            new TransactionTemplate("CARD",      "세탁소",             25_000),
            new TransactionTemplate("CARD",      "미용실",             55_000),
            new TransactionTemplate("TRANSPORT", "교통카드 충전",      65_000),
            new TransactionTemplate("TRANSPORT", "택시비",             35_000),
            new TransactionTemplate("UTILITY",   "한국전력",           68_000),
            new TransactionTemplate("UTILITY",   "도시가스",           52_000),
            new TransactionTemplate("UTILITY",   "수도요금",           25_000),
            new TransactionTemplate("PHONE",     "LG U+ 통신비",       65_000),
            new TransactionTemplate("MEDICAL",   "종합병원",           45_000),
            new TransactionTemplate("MEDICAL",   "약국",               15_000)
    );

    // ── 주식 거래 템플릿 ─────────────────────────────────────────────────────────
    // eventType: STOCK_BUY(매수·EXPENSE) / STOCK_SELL(매도·INCOME)
    // baseAmount: 단가 × 수량 기준 실거래 대금

    // ACTIVE 투자자 — 5종목(삼성전자·SK하이닉스·현대차·카카오·NAVER) 활발한 매매, 월 14건
    static final List<TransactionTemplate> NEED_IMPROVEMENT_STOCKS = List.of(
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         750_000),
            new TransactionTemplate("STOCK_BUY",  "SK하이닉스 매수",       510_000),
            new TransactionTemplate("STOCK_BUY",  "카카오 매수",           450_000),
            new TransactionTemplate("STOCK_SELL", "삼성전자 매도",         600_000),
            new TransactionTemplate("STOCK_BUY",  "NAVER 매수",            600_000),
            new TransactionTemplate("STOCK_BUY",  "현대차 매수",           460_000),
            new TransactionTemplate("STOCK_SELL", "SK하이닉스 매도",       360_000),
            new TransactionTemplate("STOCK_SELL", "카카오 매도",           450_000),
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         900_000),
            new TransactionTemplate("STOCK_SELL", "현대차 매도",           460_000),
            new TransactionTemplate("STOCK_BUY",  "SK하이닉스 매수",       720_000),
            new TransactionTemplate("STOCK_SELL", "NAVER 매도",            400_000),
            new TransactionTemplate("STOCK_SELL", "삼성전자 매도",         375_000),
            new TransactionTemplate("STOCK_BUY",  "현대차 매수",           690_000)
    );

    // NEUTRAL 투자자 — 4종목(삼성전자·LG에너지솔루션·NAVER·셀트리온) 분산매수 중심, 월 6건
    static final List<TransactionTemplate> NEED_COMPLEMENT_STOCKS = List.of(
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         600_000),
            new TransactionTemplate("STOCK_BUY",  "LG에너지솔루션 매수",   760_000),
            new TransactionTemplate("STOCK_BUY",  "NAVER 매수",            600_000),
            new TransactionTemplate("STOCK_SELL", "삼성전자 매도",         375_000),
            new TransactionTemplate("STOCK_BUY",  "셀트리온 매수",         540_000),
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         750_000)
    );

    // STABLE 투자자 — 2종목(삼성전자·KB금융) 적립식 매수 위주, 월 3건
    static final List<TransactionTemplate> STABLE_STOCKS = List.of(
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         375_000),
            new TransactionTemplate("STOCK_BUY",  "KB금융 매수",           340_000),
            new TransactionTemplate("STOCK_BUY",  "삼성전자 매수",         225_000)
    );

    private MockTransactionTemplates() {}
}
