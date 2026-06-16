package com.sol.user.usergoal.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "사용자목표")
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

    @Column(name = "월목표생활비", precision = 15, scale = 0)
    private BigDecimal monthlyTargetLivingCost;

    @Column(name = "월예상의료비", precision = 15, scale = 0)
    private BigDecimal monthlyExpectedMedicalCost;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
