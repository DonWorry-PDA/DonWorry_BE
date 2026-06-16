package com.sol.user.riskprofile.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "투자성향")
@Getter
@NoArgsConstructor
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_profile_id")
    private Long riskProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "유동성요구도")
    private Integer liquidityRequirement;

    @Column(name = "위험감내력")
    private Integer riskTolerance;

    @Column(name = "위험선호", length = 20)
    private String riskPreference;

    @Column(name = "현금흐름안정요구", length = 20)
    private String cashFlowStabilityRequirement;

    @Column(name = "floor충족률", precision = 5, scale = 2)
    private BigDecimal floorSatisfactionRate;

    @Column(name = "행동가드레일")
    private Integer behaviorGuardrail;

    @Column(name = "최종투자성향", length = 20)
    private String finalRiskPreference;

    @Column(name = "계산시각")
    private LocalDateTime calculatedAt;
}
