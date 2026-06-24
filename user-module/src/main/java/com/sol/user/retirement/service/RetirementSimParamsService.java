package com.sol.user.retirement.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.retirement.dto.RetirementSimParamsResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetirementSimParamsService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserGoalRepository userGoalRepository;
    private final PensionRepository pensionRepository;

    public RetirementSimParamsResponse getParams(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        BigDecimal totalAssets = accountRepository.findByUserUserId(userId).stream()
                .map(a -> a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal monthlyLiving = userGoalRepository.findByUserUserId(userId)
                .map(g -> g.getMonthlyTargetLivingCost() != null
                        ? g.getMonthlyTargetLivingCost()
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal monthlyPension = pensionRepository.findByUserUserId(userId).stream()
                .map(p -> p.getExpectedMonthlyAmount() == null ? BigDecimal.ZERO : p.getExpectedMonthlyAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        return RetirementSimParamsResponse.builder()
                .ageYears(user.getAge())
                .totalAssetsKrw(totalAssets)
                .monthlyLivingKrw(monthlyLiving)
                .monthlyPensionKrw(monthlyPension)
                .build();
    }
}
