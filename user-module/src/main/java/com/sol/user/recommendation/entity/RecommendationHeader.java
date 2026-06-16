package com.sol.user.recommendation.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "설계안헤더")
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

    @Column(name = "설계안유형", length = 20)
    private String recommendationType;

    @Column(name = "예상월급", precision = 15, scale = 0)
    private BigDecimal expectedMonthlySalary;

    @Column(name = "생활비충당률", precision = 5, scale = 2)
    private BigDecimal livingCostCoverageRate;

    @Column(name = "생성시각")
    private LocalDateTime createdAt;
}
