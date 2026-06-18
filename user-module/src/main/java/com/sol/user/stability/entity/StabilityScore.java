package com.sol.user.stability.entity;

import com.sol.common.entity.BaseEntity;
import com.sol.user.stability.type.LifeStabilityGrade;
import com.sol.user.stability.type.RecommendedPlanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stability_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StabilityScore extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stability_score_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 30)
    private LifeStabilityGrade grade;

    @Column(name = "cashflow_coverage_rate", precision = 8, scale = 2)
    private BigDecimal cashflowCoverageRate;

    @Column(name = "essential_expense_rate", precision = 8, scale = 2)
    private BigDecimal essentialExpenseRate;

    @Column(name = "medical_preparedness_months", precision = 8, scale = 2)
    private BigDecimal medicalPreparednessMonths;

    @Column(name = "liquidity_months", precision = 8, scale = 2)
    private BigDecimal liquidityMonths;

    @Column(name = "debt_burden_rate", precision = 8, scale = 2)
    private BigDecimal debtBurdenRate;

    @Column(name = "risk_asset_dependency_rate", precision = 8, scale = 2)
    private BigDecimal riskAssetDependencyRate;

    @Column(name = "growth_plan_allowed", nullable = false)
    private boolean growthPlanAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_plan_type", nullable = false, length = 30)
    private RecommendedPlanType recommendedPlanType;

    @Column(name = "summary_message", length = 500)
    private String summaryMessage;

    @Builder
    public StabilityScore(
            Long userId,
            int totalScore,
            LifeStabilityGrade grade,
            BigDecimal cashflowCoverageRate,
            BigDecimal essentialExpenseRate,
            BigDecimal medicalPreparednessMonths,
            BigDecimal liquidityMonths,
            BigDecimal debtBurdenRate,
            BigDecimal riskAssetDependencyRate,
            boolean growthPlanAllowed,
            RecommendedPlanType recommendedPlanType,
            String summaryMessage
    ) {
        this.userId = userId;
        this.totalScore = totalScore;
        this.grade = grade;
        this.cashflowCoverageRate = cashflowCoverageRate;
        this.essentialExpenseRate = essentialExpenseRate;
        this.medicalPreparednessMonths = medicalPreparednessMonths;
        this.liquidityMonths = liquidityMonths;
        this.debtBurdenRate = debtBurdenRate;
        this.riskAssetDependencyRate = riskAssetDependencyRate;
        this.growthPlanAllowed = growthPlanAllowed;
        this.recommendedPlanType = recommendedPlanType;
        this.summaryMessage = summaryMessage;
    }
}
