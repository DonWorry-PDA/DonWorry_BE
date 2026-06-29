package com.sol.user.mypage.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.mypage.dto.MypageResponse;
import com.sol.user.mypage.dto.MypageUpdateRequest;
import com.sol.user.mypage.dto.UserProfileResponse;
import com.sol.user.mypage.dto.UserProfileUpdateRequest;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MypageService {

    private final UserRepository userRepository;
    private final UserGoalRepository userGoalRepository;
    private final SalaryPlanRepository salaryPlanRepository;
    private final LifeStabilityService lifeStabilityService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        return UserProfileResponse.of(user, goal);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        BigDecimal monthlyTargetKrw = request.monthlyTargetKrw();
        if (monthlyTargetKrw != null && monthlyTargetKrw.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        user.updateProfile(request.age(), request.retiredValue(), request.nationalPensionReceivingValue(), now);

        UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        boolean targetChanged = isTargetChanged(monthlyTargetKrw, goal);
        if (monthlyTargetKrw != null) {
            if (goal == null) {
                goal = userGoalRepository.save(new UserGoal(user, monthlyTargetKrw, null, now));
            } else {
                goal.updateTargetLivingCost(monthlyTargetKrw, now);
            }
        }
        handleTargetLivingCostChanged(userId, targetChanged);

        return UserProfileResponse.of(user, goal);
    }

    @Transactional
    public MypageResponse update(Long userId, MypageUpdateRequest request) {
        BigDecimal monthlyTargetLivingCost = request.monthlyTargetLivingCost();
        if (monthlyTargetLivingCost != null && monthlyTargetLivingCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        user.updateProfile(request.age(), request.retired(), request.nationalPensionReceiving(), now);

        BigDecimal currentTargetLivingCost = null;
        if (monthlyTargetLivingCost != null) {
            UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
            boolean targetChanged = isTargetChanged(monthlyTargetLivingCost, goal);
            goal.updateTargetLivingCost(monthlyTargetLivingCost, now);
            currentTargetLivingCost = goal.getMonthlyTargetLivingCost();
            handleTargetLivingCostChanged(userId, targetChanged);
        }

        return new MypageResponse(
                userId,
                user.getAge(),
                user.getRetired(),
                user.getNationalPensionReceiving(),
                currentTargetLivingCost
        );
    }

    private boolean isTargetChanged(BigDecimal requestedTarget, UserGoal goal) {
        if (requestedTarget == null) {
            return false;
        }
        if (goal == null || goal.getMonthlyTargetLivingCost() == null) {
            return true;
        }
        return requestedTarget.compareTo(goal.getMonthlyTargetLivingCost()) != 0;
    }

    private void handleTargetLivingCostChanged(Long userId, boolean targetChanged) {
        if (!targetChanged) {
            return;
        }
        salaryPlanRepository.findByUserUserIdAndStatus(userId, SalaryPlan.STATUS_ACTIVE)
                .ifPresent(SalaryPlan::supersede);
        lifeStabilityService.recalculateFromUserDataIfReady(userId);
    }
}
