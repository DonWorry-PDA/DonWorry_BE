package com.sol.user.recommendation.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_header")
@Getter
@NoArgsConstructor
public class RecommendationHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rec_id")
    private Long recId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recommendation_type", length = 20)
    private String recommendationType;

    @Column(name = "expected_monthly_salary", precision = 15, scale = 0)
    private BigDecimal expectedMonthlySalary;

    @Column(name = "living_cost_coverage_rate", precision = 5, scale = 2)
    private BigDecimal livingCostCoverageRate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
