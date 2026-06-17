package com.sol.user.recommendation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "recommendation_item")
@Getter
@NoArgsConstructor
public class RecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rec_id", nullable = false)
    private RecommendationHeader recommendationHeader;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "allocated_amount", precision = 15, scale = 0)
    private BigDecimal allocatedAmount;

    @Column(name = "product_contribution", precision = 15, scale = 0)
    private BigDecimal productContribution;

    @Column(name = "recommended_account_type", length = 20)
    private String recommendedAccountType;
}
