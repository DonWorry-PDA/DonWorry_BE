package com.sol.user.mypage.dto;

import com.sol.user.user.entity.User;
import com.sol.user.usergoal.entity.UserGoal;

import java.math.BigDecimal;

public record UserProfileResponse(
        String name,
        Integer age,
        String status,
        String pensionStatus,
        BigDecimal monthlyTargetKrw
) {
    public static UserProfileResponse of(User user, UserGoal goal) {
        return new UserProfileResponse(
                user.getName(),
                user.getAge(),
                Boolean.TRUE.equals(user.getRetired()) ? "은퇴 후" : "은퇴 전",
                Boolean.TRUE.equals(user.getNationalPensionReceiving()) ? "수령 중" : "수령 전",
                goal != null ? goal.getMonthlyTargetLivingCost() : null
        );
    }
}
