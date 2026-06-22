package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.GuidanceBand;
import com.sol.user.portfolio.type.RecommendationTrack;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 은퇴 포트폴리오 추천 최종 응답 (STEP1~6 조립).
 * 분기는 track + 가변 plans로 표현: 위험중립형 2안 / 구조적부족 0안 / 연금초과(α충족률 null).
 */
@Getter
@Builder
public class RecommendationResponse {

    private RecommendationTrack track;
    private BigDecimal alpha;
    private GuidanceBand band;                 // NORMAL일 때만

    private List<PlanResponse> plans;          // 구조적부족이면 빈 리스트

    private String q3ReferenceLabel;           // Q3 표 기준 안내 (예: "안정안 기준 예시")
    private List<Q3Scenario> q3Scenarios;
}
