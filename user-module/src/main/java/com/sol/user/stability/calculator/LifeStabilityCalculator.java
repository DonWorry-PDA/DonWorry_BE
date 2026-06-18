package com.sol.user.stability.calculator;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.dto.LifeStabilityCalculationInput;
import com.sol.user.stability.type.LifeStabilityGrade;
import com.sol.user.stability.type.RecommendedPlanType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class LifeStabilityCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int SCALE = 2;

    public LifeStabilityCalculatedResult calculate(LifeStabilityCalculationInput input) {
        validate(input);

        BigDecimal securedCashflow = input.monthlyIncome()
                .add(input.monthlyFinancialIncome())
                .subtract(input.monthlyFixedExpense());

        BigDecimal cashflowCoverageRate = percentage(
                securedCashflow,
                input.targetMonthlyLivingExpense()
        );

        BigDecimal essentialExpenseRate = percentage(
                input.monthlyEssentialExpense(),
                input.monthlyIncome()
        );

        BigDecimal monthlyMedicalExpense = input.expectedAnnualMedicalExpense()
                .divide(TWELVE, SCALE, RoundingMode.HALF_UP);
        BigDecimal medicalPreparednessMonths = months(
                input.medicalPreparedAsset(),
                monthlyMedicalExpense
        );

        BigDecimal liquidityMonths = months(
                input.liquidAsset(),
                input.monthlyEssentialExpense()
        );

        BigDecimal debtBurdenRate = percentage(
                input.monthlyLoanRepayment(),
                input.monthlyIncome()
        );

        BigDecimal monthlyShortage = input.targetMonthlyLivingExpense().subtract(securedCashflow);
        if (monthlyShortage.compareTo(BigDecimal.ZERO) < 0) {
            monthlyShortage = BigDecimal.ZERO;
        }

        BigDecimal riskAssetDependencyRate = percentage(
                monthlyShortage,
                input.targetMonthlyLivingExpense()
        );

        int cashflowScore = scoreCashflowCoverage(cashflowCoverageRate);
        int essentialExpenseScore = scoreEssentialExpense(essentialExpenseRate);
        int medicalPreparednessScore = scoreMedicalPreparedness(medicalPreparednessMonths);
        int liquidityScore = scoreLiquidity(liquidityMonths);
        int debtBurdenScore = scoreDebtBurden(debtBurdenRate);
        int riskAssetDependencyScore = scoreRiskAssetDependency(riskAssetDependencyRate);

        int totalScore = cashflowScore
                + essentialExpenseScore
                + medicalPreparednessScore
                + liquidityScore
                + debtBurdenScore
                + riskAssetDependencyScore;

        LifeStabilityGrade grade = LifeStabilityGrade.fromScore(totalScore);
        boolean growthPlanAllowed = grade == LifeStabilityGrade.STABLE
                && liquidityMonths.compareTo(BigDecimal.valueOf(12)) >= 0
                && medicalPreparednessMonths.compareTo(BigDecimal.valueOf(24)) >= 0
                && debtBurdenRate.compareTo(BigDecimal.valueOf(10)) <= 0;

        boolean balancedPlanAllowed = grade == LifeStabilityGrade.STABLE
                && liquidityMonths.compareTo(BigDecimal.valueOf(6)) >= 0
                && medicalPreparednessMonths.compareTo(BigDecimal.valueOf(12)) >= 0
                && debtBurdenRate.compareTo(BigDecimal.valueOf(20)) <= 0;

        RecommendedPlanType recommendedPlanType = RecommendedPlanType.STABLE_INCOME;
        if (growthPlanAllowed) {
            recommendedPlanType = RecommendedPlanType.GROWTH_EXTRA_ASSET;
        } else if (balancedPlanAllowed) {
            recommendedPlanType = RecommendedPlanType.BALANCED_INCOME;
        }

        return LifeStabilityCalculatedResult.builder()
                .totalScore(totalScore)
                .grade(grade)
                .cashflowCoverageRate(cashflowCoverageRate)
                .essentialExpenseRate(essentialExpenseRate)
                .medicalPreparednessMonths(medicalPreparednessMonths)
                .liquidityMonths(liquidityMonths)
                .debtBurdenRate(debtBurdenRate)
                .riskAssetDependencyRate(riskAssetDependencyRate)
                .growthPlanAllowed(growthPlanAllowed)
                .recommendedPlanType(recommendedPlanType)
                .build();
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        return numerator
                .multiply(HUNDRED)
                .divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal months(BigDecimal asset, BigDecimal monthlyExpense) {
        if (asset == null || monthlyExpense == null || monthlyExpense.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        return asset.divide(monthlyExpense, SCALE, RoundingMode.HALF_UP);
    }

    private void validate(LifeStabilityCalculationInput input) {
        if (input == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        requirePositive(input.targetMonthlyLivingExpense());
        requirePositive(input.monthlyIncome());
        requirePositive(input.monthlyEssentialExpense());
        requirePositive(input.expectedAnnualMedicalExpense());
        requireNonNegative(input.monthlyFixedExpense());
        requireNonNegative(input.monthlyLoanRepayment());
        requireNonNegative(input.monthlyFinancialIncome());
        requireNonNegative(input.liquidAsset());
        requireNonNegative(input.medicalPreparedAsset());
    }

    private void requirePositive(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireNonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private int scoreCashflowCoverage(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return 30;
        }
        if (rate.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return 25;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return 18;
        }
        if (rate.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return 10;
        }
        return 5;
    }

    private int scoreEssentialExpense(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return 20;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return 15;
        }
        if (rate.compareTo(BigDecimal.valueOf(90)) <= 0) {
            return 8;
        }
        return 3;
    }

    private int scoreMedicalPreparedness(BigDecimal months) {
        if (months.compareTo(BigDecimal.valueOf(24)) >= 0) {
            return 15;
        }
        if (months.compareTo(BigDecimal.valueOf(12)) >= 0) {
            return 11;
        }
        if (months.compareTo(BigDecimal.valueOf(6)) >= 0) {
            return 7;
        }
        return 3;
    }

    private int scoreLiquidity(BigDecimal months) {
        if (months.compareTo(BigDecimal.valueOf(12)) >= 0) {
            return 15;
        }
        if (months.compareTo(BigDecimal.valueOf(6)) >= 0) {
            return 12;
        }
        if (months.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return 8;
        }
        return 3;
    }

    private int scoreDebtBurden(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(10)) <= 0) {
            return 10;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return 7;
        }
        if (rate.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return 4;
        }
        return 1;
    }

    private int scoreRiskAssetDependency(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return 10;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return 8;
        }
        if (rate.compareTo(BigDecimal.valueOf(40)) <= 0) {
            return 5;
        }
        return 2;
    }
}
