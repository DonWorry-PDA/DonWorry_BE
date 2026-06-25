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

    private MockTransactionTemplates() {}
}
