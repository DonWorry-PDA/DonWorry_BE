package com.sol.user.user.entity;

import com.sol.user.config.IdNumberConverter;
import com.sol.user.portfolio.type.InvestmentPropensity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "age")
    private Integer age;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "retired")
    private Boolean retired;

    @Column(name = "national_pension_receiving")
    private Boolean nationalPensionReceiving;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "phone", length = 20)
    private String phone;

    @Convert(converter = IdNumberConverter.class)
    @Column(name = "id_number", length = 255)
    private String idNumber;

    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted = false;

    /**
     * 증권사 적합성진단(KYC) 투자자성향 — 우리 운용등급과 별개의 외부 보유값.
     * 앱이 산출하지 않으며 마이데이터·증권 연동으로 채워진다(writer는 연동 이슈에서 추가).
     * 연동 전엔 null이라 추천 조립 시 {@code PortfolioConstants.DEFAULT_PROPENSITY}로 대체된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_propensity", length = 20)
    private InvestmentPropensity investmentPropensity;

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    /** 증권 적합성진단(KYC) 성향 반영 — 마이데이터/증권 연동(현재는 목업 시더)이 호출하는 진입점. */
    public void assignInvestmentPropensity(InvestmentPropensity investmentPropensity) {
        this.investmentPropensity = investmentPropensity;
    }

    public void updateProfile(Integer age, Boolean retired,
                              Boolean nationalPensionReceiving, LocalDateTime now) {
        if (age != null) {
            this.age = age;
        }
        if (retired != null) {
            this.retired = retired;
        }
        if (nationalPensionReceiving != null) {
            this.nationalPensionReceiving = nationalPensionReceiving;
        }
        this.updatedAt = now;
        this.onboardingCompleted = true;
    }

}
