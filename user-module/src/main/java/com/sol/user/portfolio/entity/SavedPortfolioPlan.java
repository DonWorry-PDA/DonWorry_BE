package com.sol.user.portfolio.entity;

import com.sol.user.portfolio.type.PlanType;
import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saved_portfolio_plan")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SavedPortfolioPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 20, nullable = false)
    private PlanType planType;

    @Column(name = "monthly_income", precision = 15, scale = 0)
    private BigDecimal monthlyIncome;

    @Column(name = "current_coverage_rate", precision = 5, scale = 2)
    private BigDecimal currentCoverageRate;

    @Column(name = "total_coverage_rate", precision = 5, scale = 2)
    private BigDecimal totalCoverageRate;

    @Column(name = "current_monthly_shortfall", precision = 15, scale = 0)
    private BigDecimal currentMonthlyShortfall;

    @Column(name = "residual_monthly_shortfall", precision = 15, scale = 0)
    private BigDecimal residualMonthlyShortfall;

    @Column(name = "principal_amount", precision = 15, scale = 0)
    private BigDecimal principalAmount;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @OneToMany(mappedBy = "savedPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SavedPortfolioPlanItem> items = new ArrayList<>();

    public void update(PlanType planType, BigDecimal monthlyIncome,
                       BigDecimal currentCoverageRate, BigDecimal totalCoverageRate,
                       BigDecimal currentMonthlyShortfall, BigDecimal residualMonthlyShortfall,
                       BigDecimal principalAmount, LocalDateTime savedAt) {
        this.planType = planType;
        this.monthlyIncome = monthlyIncome;
        this.currentCoverageRate = currentCoverageRate;
        this.totalCoverageRate = totalCoverageRate;
        this.currentMonthlyShortfall = currentMonthlyShortfall;
        this.residualMonthlyShortfall = residualMonthlyShortfall;
        this.principalAmount = principalAmount;
        this.savedAt = savedAt;
        this.items.clear();
    }

    public void addItem(SavedPortfolioPlanItem item) {
        items.add(item);
        item.assignPlan(this);
    }
}
