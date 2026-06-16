package com.sol.user.stability.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "생활안정도점수")
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

    @Column(name = "생활비충당률", precision = 5, scale = 2)
    private BigDecimal livingCostCoverageRate;

    @Column(name = "필수지출부담률", precision = 5, scale = 2)
    private BigDecimal essentialExpenseBurdenRate;

    @Column(name = "유동성")
    private Integer liquidity;

    @Column(name = "의료비대비력", precision = 5, scale = 2)
    private BigDecimal medicalCostPreparedness;

    @Column(name = "부채부담률", precision = 5, scale = 2)
    private BigDecimal debtBurdenRate;

    @Column(name = "위험자산의존도", precision = 5, scale = 2)
    private BigDecimal riskyAssetDependency;

    @Column(name = "안정등급", length = 10)
    private String stabilityGrade;

    @Column(name = "계산시각")
    private LocalDateTime calculatedAt;
}
