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

    // 설계안 적용 전 현황 — 화면의 "59% → 84%" 비교 표시용
    private BigDecimal targetMonthlyLivingCost;   // 목표 생활비
    private BigDecimal currentMonthlyCashFlow;    // 현재 월 현금흐름 (국민연금 + 배당)
    private BigDecimal currentCoverageRate;       // 현재 생활비 충당률 % (예: 59)
    private BigDecimal currentMonthlyShortfall;   // 현재 월 부족액 (예: 90만)

    private String q3ReferenceLabel;           // Q3 표 기준 안내 (예: "안정안 기준 예시")
    private List<Q3Scenario> q3Scenarios;

    /** 현재 BROKERAGE 예수금. 이체 필요액 = max(0, Σholdings[].amount − brokerageBalance). */
    private BigDecimal brokerageBalance;
}
