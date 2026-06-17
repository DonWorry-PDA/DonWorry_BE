package com.sol.user.stability.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stability_score")
@Getter
@NoArgsConstructor
public class StabilityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stability_score_id")
    private Long stabilityScoreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "living_cost_coverage_rate", precision = 5, scale = 2)
    private BigDecimal livingCostCoverageRate;

    @Column(name = "essential_expense_burden_rate", precision = 5, scale = 2)
    private BigDecimal essentialExpenseBurdenRate;

    @Column(name = "liquidity")
    private Integer liquidity;

    @Column(name = "medical_cost_preparedness", precision = 5, scale = 2)
    private BigDecimal medicalCostPreparedness;

    @Column(name = "debt_burden_rate", precision = 5, scale = 2)
    private BigDecimal debtBurdenRate;

    @Column(name = "risky_asset_dependency", precision = 5, scale = 2)
    private BigDecimal riskyAssetDependency;

    @Column(name = "stability_grade", length = 10)
    private String stabilityGrade;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}
