package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 안별 추천 응답 — STEP5 배분(PlanAllocation)과 STEP6 수령(PlanCoverage)을 병합.
 */
@Getter
@Builder
public class PlanResponse {

    private PlanType type;
    private String label;
    private String description;

    // 배분 (STEP5)
    private BigDecimal riskTarget;
    private BigDecimal safeTarget;
    private BigDecimal shortTermBucket;
    private List<Holding> holdings;

    // 수령 (STEP6)
    private BigDecimal monthlyIncome;
    private BigDecimal alphaCoverageRate;   // α≤0이면 null
    private BigDecimal inheritanceAmount;

    public static PlanResponse of(PlanAllocation allocation, PlanCoverage coverage) {
        return PlanResponse.builder()
                .type(allocation.getType())
                .label(allocation.getType().getLabel())
                .description(allocation.getType().getDescription())
                .riskTarget(allocation.getRiskTarget())
                .safeTarget(allocation.getSafeTarget())
                .shortTermBucket(allocation.getShortTermBucket())
                .holdings(allocation.getHoldings())
                .monthlyIncome(coverage.getMonthlyIncome())
                .alphaCoverageRate(coverage.getAlphaCoverageRate())
                .inheritanceAmount(coverage.getInheritanceAmount())
                .build();
    }
}
