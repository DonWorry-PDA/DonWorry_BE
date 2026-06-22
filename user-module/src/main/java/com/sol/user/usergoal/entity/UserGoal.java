package com.sol.user.usergoal.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_goal")
@Getter
@NoArgsConstructor
public class UserGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_goal_id")
    private Long userGoalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "monthly_target_living_cost", precision = 15, scale = 0)
    private BigDecimal monthlyTargetLivingCost;

    @Column(name = "monthly_expected_medical_cost", precision = 15, scale = 0)
    private BigDecimal monthlyExpectedMedicalCost;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserGoal(User user, BigDecimal monthlyTargetLivingCost,
                    BigDecimal monthlyExpectedMedicalCost, LocalDateTime now) {
        this.user = user;
        this.monthlyTargetLivingCost = monthlyTargetLivingCost;
        this.monthlyExpectedMedicalCost = monthlyExpectedMedicalCost;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
