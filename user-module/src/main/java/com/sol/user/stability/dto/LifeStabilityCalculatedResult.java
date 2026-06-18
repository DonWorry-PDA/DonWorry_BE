package com.sol.user.stability.dto;

import com.sol.user.stability.type.LifeStabilityGrade;
import com.sol.user.stability.type.RecommendedPlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class LifeStabilityCalculatedResult {

    private int totalScore;
    private LifeStabilityGrade grade;

    private BigDecimal cashflowCoverageRate;
    private BigDecimal essentialExpenseRate;
    private BigDecimal medicalPreparednessMonths;
    private BigDecimal liquidityMonths;
    private BigDecimal debtBurdenRate;
    private BigDecimal riskAssetDependencyRate;

    private boolean growthPlanAllowed;
    private RecommendedPlanType recommendedPlanType;
}
