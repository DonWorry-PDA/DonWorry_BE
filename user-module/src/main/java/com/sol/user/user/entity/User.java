package com.sol.user.user.entity;

import com.sol.user.config.IdNumberConverter;
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

    public void completeOnboarding() {
        this.onboardingCompleted = true;
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
