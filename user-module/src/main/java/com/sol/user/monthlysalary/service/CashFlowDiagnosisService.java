package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.pension.repository.PensionRepository;
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
public class CashFlowDiagnosisService {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";

    private final PensionRepository pensionRepository;
    private final UserGoalRepository userGoalRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CashFlowDiagnosisResponse diagnose(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        BigDecimal nationalPension = pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO);

        BigDecimal dividendIncome = nullToZero(holdingRepository.sumMonthlyDividendByUserId(userId));

        BigDecimal targetMonthlyLivingCost = userGoalRepository.findByUserUserId(userId)
                .map(UserGoal::getMonthlyTargetLivingCost)
                .orElse(BigDecimal.ZERO);

        BigDecimal monthlyCashFlow = nationalPension.add(dividendIncome);
        BigDecimal monthlyShortfall = targetMonthlyLivingCost.subtract(monthlyCashFlow).max(BigDecimal.ZERO);

        // 반올림은 응답 단계에서만 (원 단위). 중간 계산은 전체 정밀도 유지.
        return CashFlowDiagnosisResponse.builder()
                .monthlyCashFlow(toWon(monthlyCashFlow))
                .nationalPension(toWon(nationalPension))
                .dividendIncome(toWon(dividendIncome))
                .targetMonthlyLivingCost(toWon(targetMonthlyLivingCost))
                .monthlyShortfall(toWon(monthlyShortfall))
                .shortfallExists(monthlyShortfall.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal toWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
