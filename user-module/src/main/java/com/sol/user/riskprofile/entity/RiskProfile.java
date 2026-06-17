package com.sol.user.riskprofile.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_profile")
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

    @Column(name = "liquidity_requirement")
    private Integer liquidityRequirement;

    @Column(name = "risk_tolerance")
    private Integer riskTolerance;

    @Column(name = "risk_preference", length = 20)
    private String riskPreference;

    @Column(name = "cash_flow_stability_requirement", length = 20)
    private String cashFlowStabilityRequirement;

    @Column(name = "floor_satisfaction_rate", precision = 5, scale = 2)
    private BigDecimal floorSatisfactionRate;

    @Column(name = "behavior_guardrail")
    private Integer behaviorGuardrail;

    @Column(name = "final_risk_preference", length = 20)
    private String finalRiskPreference;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}
