package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.GuidanceBand;
import com.sol.user.portfolio.type.RecommendationTrack;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * STEP6 결과. track은 STEP5 결과에 α≤0 판정을 더해 최종 확정(순서로 방어 — 구조적부족 우선).
 */
@Getter
@Builder
public class CoverageResult {

    private RecommendationTrack track;
    private BigDecimal alpha;                 // 목표생활비 − 국민연금 − 기타정기수입
    private GuidanceBand band;                // NORMAL일 때만 (그 외 null)
    private List<PlanCoverage> planCoverages; // 구조적부족이면 비움
    private List<Q3Scenario> q3Scenarios;     // 대표안 기준 3옵션
}
