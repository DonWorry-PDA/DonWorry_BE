package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 안별 추천 응답 — STEP5 배분(PlanAllocation)과 STEP6 수령(PlanCoverage)을 병합하고
 * 화면용 필드(displayName/status/allocations)까지 빚어 내려준다. 조립은 PortfolioRecommendationMapper.
 */
@Getter
@Builder
public class PlanResponse {

    private PlanType type;
    private String label;          // 도메인 라벨 (예: "안정안")
    private String displayName;    // 화면 표시명 (예: "안정 월급형")
    private String description;
    private PlanStatus status;

    // 배분 (STEP5)
    private BigDecimal riskTarget;
    private BigDecimal safeTarget;
    private BigDecimal shortTermBucket;
    private List<Holding> holdings;
    private List<AllocationView> allocations;   // 화면 배분 항목 (안전·위험·단기 병합, 비중 %)

    // 수령 (STEP6)
    private BigDecimal monthlyIncome;
    private BigDecimal alphaCoverageRate;   // α≤0이면 null
    private BigDecimal inheritanceAmount;
}
