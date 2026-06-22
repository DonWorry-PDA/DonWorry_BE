package com.sol.user.portfolio.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추천 분기 트랙 (STEP5/6). 분기를 데이터로 표현해 응답 구조로 흡수한다.
 */
@Getter
@RequiredArgsConstructor
public enum RecommendationTrack {
    NORMAL("정상 — 여유분 기반 3안(또는 위험중립형 2안) 추천"),
    STRUCTURAL_SHORTAGE("구조적 부족 — 여유분<=0, 전액 안전자산 운용 안내"),
    PENSION_SUFFICIENT("연금 충족 — α<=0, 여유 증식·상속 관점 운용");

    private final String description;
}
