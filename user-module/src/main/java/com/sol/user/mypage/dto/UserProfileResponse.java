package com.sol.user.mypage.dto;

import com.sol.user.user.entity.User;
import com.sol.user.usergoal.entity.UserGoal;

import java.math.BigDecimal;

public record UserProfileResponse(
        String name,
        Integer age,
        String status,
        String pensionStatus,
        BigDecimal monthlyTargetKrw,
        // 이번 수정으로 ACTIVE 월급 설계안이 비활성화됐는지 — FE가 "설계안 다시 맞춰야 해요" 안내에 사용.
        Boolean activePlanSuperseded
) {
    public static UserProfileResponse of(User user, UserGoal goal) {
        return of(user, goal, false);
    }

    public static UserProfileResponse of(User user, UserGoal goal, boolean activePlanSuperseded) {
        return new UserProfileResponse(
                user.getName(),
                user.getAge(),
                Boolean.TRUE.equals(user.getRetired()) ? "은퇴 후" : "은퇴 전",
                Boolean.TRUE.equals(user.getNationalPensionReceiving()) ? "수령 중" : "수령 전",
                goal != null ? goal.getMonthlyTargetLivingCost() : null,
                activePlanSuperseded
        );
    }
}
