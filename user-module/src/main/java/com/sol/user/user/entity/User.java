package com.sol.user.user.entity;

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

    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted = false;

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    public void applyMyDataMockProfile(boolean retired, boolean nationalPensionReceiving) {
        this.retired = retired;
        this.nationalPensionReceiving = nationalPensionReceiving;
        this.onboardingCompleted = true;
        this.updatedAt = LocalDateTime.now();
    }
}
