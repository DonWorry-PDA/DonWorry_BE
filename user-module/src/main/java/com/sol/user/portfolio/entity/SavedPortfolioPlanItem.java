package com.sol.user.portfolio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "saved_portfolio_plan_item")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SavedPortfolioPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_plan_id", nullable = false)
    private SavedPortfolioPlan savedPlan;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(name = "ticker", length = 20)
    private String ticker;

    @Column(name = "weight", precision = 10, scale = 4)
    private BigDecimal weight;

    @Column(name = "target_amount", precision = 15, scale = 0)
    private BigDecimal targetAmount;

    void assignPlan(SavedPortfolioPlan savedPlan) {
        this.savedPlan = savedPlan;
    }
}
