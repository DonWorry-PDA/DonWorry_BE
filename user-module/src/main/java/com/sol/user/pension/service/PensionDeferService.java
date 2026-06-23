package com.sol.user.pension.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.calculator.PensionDeferCalculator;
import com.sol.user.pension.dto.PensionDeferComparisonRow;
import com.sol.user.pension.dto.PensionDeferDetail;
import com.sol.user.pension.dto.PensionDeferResponse;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PensionDeferService {

    private static final Set<Integer> VALID_DEFER_RATES = Set.of(0, 50, 60, 70, 80, 90, 100);

    private final PensionRepository pensionRepository;
    private final HoldingRepository holdingRepository;
    private final UserGoalRepository userGoalRepository;
    private final PensionDeferCalculator calculator;

    @Transactional(readOnly = true)
    public PensionDeferResponse compare(Long userId, int deferRate, int deferYears) {
        if (!VALID_DEFER_RATES.contains(deferRate)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        if (deferYears < 1 || deferYears > 5) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        BigDecimal base = pensionRepository.findMonthlyAmount(userId, "NATIONAL")
            .orElseThrow(() -> new BaseException(ErrorCode.PENSION_NOT_FOUND));

        BigDecimal dividendIncome = holdingRepository.sumMonthlyDividendByUserId(userId);

        BigDecimal targetLivingCost = userGoalRepository.findByUserUserId(userId)
            .map(UserGoal::getMonthlyTargetLivingCost)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_GOAL_NOT_FOUND));

        List<PensionDeferComparisonRow> rows =
            calculator.calcAllRows(base, deferYears, dividendIncome, targetLivingCost);

        PensionDeferComparisonRow selectedRow = rows.stream()
            .filter(r -> r.deferRate() == deferRate)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No row for deferRate: " + deferRate));

        int coverageRateBefore = rows.stream()
            .filter(r -> r.deferRate() == 0)
            .findFirst()
            .map(PensionDeferComparisonRow::coverageRateAfter)
            .orElseThrow(() -> new IllegalStateException("No row found for deferRate=0"));

        String insight = calculator.generateInsight(deferRate, rows);

        PensionDeferDetail detail = PensionDeferDetail.builder()
            .deferRate(deferRate)
            .deferYears(deferYears)
            .basePensionMonthly(base.setScale(0, RoundingMode.DOWN).longValue())
            .duringDeferMonthly(selectedRow.duringDeferMonthly())
            .afterDeferMonthly(selectedRow.afterDeferMonthly())
            .monthlyIncrease(selectedRow.monthlyIncrease())
            .breakEvenMonths(selectedRow.breakEvenMonths())
            .coverageRateBefore(coverageRateBefore)
            .coverageRateAfter(selectedRow.coverageRateAfter())
            .insight(insight)
            .build();

        return PensionDeferResponse.builder()
            .selected(detail)
            .comparisonTable(rows)
            .build();
    }
}
