package com.sol.user.mypage.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.mypage.dto.MypageResponse;
import com.sol.user.mypage.dto.MypageUpdateRequest;
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
            goal.updateTargetLivingCost(monthlyTargetLivingCost, now);
            currentTargetLivingCost = goal.getMonthlyTargetLivingCost();
        }

        return new MypageResponse(
                userId,
                user.getAge(),
                user.getRetired(),
                user.getNationalPensionReceiving(),
                currentTargetLivingCost
        );
    }
}
