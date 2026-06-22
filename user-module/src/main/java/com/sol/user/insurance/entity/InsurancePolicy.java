package com.sol.user.insurance.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "insurance_policy")
@Getter
@NoArgsConstructor
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insurance_policy_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "institution_name", nullable = false, length = 50)
    private String institutionName;

    @Column(name = "insurance_type", nullable = false, length = 30)
    private String insuranceType;

    @Column(name = "monthly_premium", nullable = false, precision = 15, scale = 0)
    private BigDecimal monthlyPremium;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "medical_reserve", nullable = false, precision = 15, scale = 0)
    private BigDecimal medicalReserve;

    public InsurancePolicy(User user, String institutionName, String insuranceType,
                           BigDecimal monthlyPremium, Boolean active, BigDecimal medicalReserve) {
        this.user = user;
        this.institutionName = institutionName;
        this.insuranceType = insuranceType;
        this.monthlyPremium = monthlyPremium;
        this.active = active;
        this.medicalReserve = medicalReserve;
    }

    public void updateMock(String institutionName, BigDecimal monthlyPremium,
                           Boolean active, BigDecimal medicalReserve) {
        this.institutionName = institutionName;
        this.monthlyPremium = monthlyPremium;
        this.active = active;
        this.medicalReserve = medicalReserve;
    }
}
