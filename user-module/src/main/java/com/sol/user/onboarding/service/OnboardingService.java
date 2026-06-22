package com.sol.user.onboarding.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.onboarding.dto.OnboardingRequest;
import com.sol.user.onboarding.dto.OnboardingResponse;
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
public class OnboardingService {

    /** 시연 기본값: 마이데이터 목업이 건드리지 않는 목표 생활비/예상 의료비. */
    private static final BigDecimal DEFAULT_TARGET_LIVING_COST = BigDecimal.valueOf(2_200_000);
    private static final BigDecimal DEFAULT_EXPECTED_MEDICAL_COST = BigDecimal.valueOf(350_000);

    private final UserRepository userRepository;
    private final UserGoalRepository userGoalRepository;

    @Transactional
    public OnboardingResponse complete(Long userId, OnboardingRequest request) {
        if (request == null) {
            request = new OnboardingRequest(null, null, null, null, null);
        }
        BigDecimal targetLivingCost = orDefault(request.monthlyTargetLivingCost(), DEFAULT_TARGET_LIVING_COST);
        BigDecimal expectedMedicalCost = orDefault(request.monthlyExpectedMedicalCost(), DEFAULT_EXPECTED_MEDICAL_COST);
        requirePositive(targetLivingCost);
        requirePositive(expectedMedicalCost);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        user.updateProfile(request.age(), request.retired(), request.nationalPensionReceiving(), now);

        UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .map(existing -> {
                    existing.updateMock(targetLivingCost, expectedMedicalCost, now);
                    return existing;
                })
                .orElseGet(() -> new UserGoal(user, targetLivingCost, expectedMedicalCost, now));
        UserGoal saved = userGoalRepository.save(goal);

        return new OnboardingResponse(
                userId,
                user.getAge(),
                user.getRetired(),
                user.getNationalPensionReceiving(),
                saved.getMonthlyTargetLivingCost(),
                saved.getMonthlyExpectedMedicalCost(),
                user.getOnboardingCompleted()
        );
    }

    private BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private void requirePositive(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }
}
