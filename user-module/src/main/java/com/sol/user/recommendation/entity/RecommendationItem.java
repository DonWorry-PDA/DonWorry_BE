package com.sol.user.recommendation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "설계안구성")
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

    @Column(name = "비중", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "배분금액", precision = 15, scale = 0)
    private BigDecimal allocatedAmount;

    @Column(name = "상품기여도", precision = 15, scale = 0)
    private BigDecimal productContribution;

    @Column(name = "추천계좌유형", length = 20)
    private String recommendedAccountType;
}
