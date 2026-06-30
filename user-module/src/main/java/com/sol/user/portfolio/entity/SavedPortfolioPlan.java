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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
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

    public void addItem(SavedPortfolioPlanItem item) {
        items.add(item);
        item.assignPlan(this);
    }
}
