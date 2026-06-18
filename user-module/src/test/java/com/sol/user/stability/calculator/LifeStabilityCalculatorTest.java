package com.sol.user.stability.calculator;

import com.sol.common.exception.BaseException;
import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.dto.LifeStabilityCalculationInput;
import com.sol.user.stability.type.LifeStabilityGrade;
import com.sol.user.stability.type.RecommendedPlanType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifeStabilityCalculatorTest {

    private final LifeStabilityCalculator calculator = new LifeStabilityCalculator();

    @Test
    void calculateReturnsExpectedSampleResult() {
        LifeStabilityCalculationInput input = LifeStabilityCalculationInput.builder()
                .targetMonthlyLivingExpense(BigDecimal.valueOf(2_500_000))
                .monthlyIncome(BigDecimal.valueOf(1_800_000))
                .monthlyFixedExpense(BigDecimal.valueOf(700_000))
                .monthlyEssentialExpense(BigDecimal.valueOf(1_200_000))
                .monthlyLoanRepayment(BigDecimal.valueOf(200_000))
                .monthlyFinancialIncome(BigDecimal.valueOf(300_000))
                .liquidAsset(BigDecimal.valueOf(7_000_000))
                .medicalPreparedAsset(BigDecimal.valueOf(5_000_000))
                .expectedAnnualMedicalExpense(BigDecimal.valueOf(6_000_000))
                .build();

        LifeStabilityCalculatedResult result = calculator.calculate(input);

        assertThat(result.getTotalScore()).isEqualTo(49);
        assertThat(result.getGrade()).isEqualTo(LifeStabilityGrade.CAUTION);
        assertThat(result.getCashflowCoverageRate()).isEqualByComparingTo("56.00");
        assertThat(result.getEssentialExpenseRate()).isEqualByComparingTo("66.67");
        assertThat(result.getMedicalPreparednessMonths()).isEqualByComparingTo("10.00");
        assertThat(result.getLiquidityMonths()).isEqualByComparingTo("5.83");
        assertThat(result.getDebtBurdenRate()).isEqualByComparingTo("11.11");
        assertThat(result.getRiskAssetDependencyRate()).isEqualByComparingTo("44.00");
        assertThat(result.isGrowthPlanAllowed()).isFalse();
        assertThat(result.getRecommendedPlanType()).isEqualTo(RecommendedPlanType.STABLE_INCOME);
    }

    @Test
    void calculateRecommendsBalancedIncomeWhenStableButGrowthConditionsAreNotEnough() {
        LifeStabilityCalculationInput input = LifeStabilityCalculationInput.builder()
                .targetMonthlyLivingExpense(BigDecimal.valueOf(2_000_000))
                .monthlyIncome(BigDecimal.valueOf(3_000_000))
                .monthlyFixedExpense(BigDecimal.valueOf(500_000))
                .monthlyEssentialExpense(BigDecimal.valueOf(1_000_000))
                .monthlyLoanRepayment(BigDecimal.valueOf(600_000))
                .monthlyFinancialIncome(BigDecimal.ZERO)
                .liquidAsset(BigDecimal.valueOf(6_000_000))
                .medicalPreparedAsset(BigDecimal.valueOf(12_000_000))
                .expectedAnnualMedicalExpense(BigDecimal.valueOf(12_000_000))
                .build();

        LifeStabilityCalculatedResult result = calculator.calculate(input);

        assertThat(result.getGrade()).isEqualTo(LifeStabilityGrade.STABLE);
        assertThat(result.isGrowthPlanAllowed()).isFalse();
        assertThat(result.getRecommendedPlanType()).isEqualTo(RecommendedPlanType.BALANCED_INCOME);
    }

    @Test
    void calculateRecommendsGrowthExtraAssetWhenAllGrowthConditionsAreMet() {
        LifeStabilityCalculationInput input = LifeStabilityCalculationInput.builder()
                .targetMonthlyLivingExpense(BigDecimal.valueOf(2_000_000))
                .monthlyIncome(BigDecimal.valueOf(3_000_000))
                .monthlyFixedExpense(BigDecimal.valueOf(500_000))
                .monthlyEssentialExpense(BigDecimal.valueOf(1_000_000))
                .monthlyLoanRepayment(BigDecimal.valueOf(300_000))
                .monthlyFinancialIncome(BigDecimal.ZERO)
                .liquidAsset(BigDecimal.valueOf(12_000_000))
                .medicalPreparedAsset(BigDecimal.valueOf(24_000_000))
                .expectedAnnualMedicalExpense(BigDecimal.valueOf(12_000_000))
                .build();

        LifeStabilityCalculatedResult result = calculator.calculate(input);

        assertThat(result.getGrade()).isEqualTo(LifeStabilityGrade.STABLE);
        assertThat(result.isGrowthPlanAllowed()).isTrue();
        assertThat(result.getRecommendedPlanType()).isEqualTo(RecommendedPlanType.GROWTH_EXTRA_ASSET);
    }

    @Test
    void calculateThrowsExceptionWhenRequiredDenominatorIsZero() {
        LifeStabilityCalculationInput input = LifeStabilityCalculationInput.builder()
                .targetMonthlyLivingExpense(BigDecimal.ZERO)
                .monthlyIncome(BigDecimal.valueOf(1_800_000))
                .monthlyFixedExpense(BigDecimal.valueOf(700_000))
                .monthlyEssentialExpense(BigDecimal.valueOf(1_200_000))
                .monthlyLoanRepayment(BigDecimal.valueOf(200_000))
                .monthlyFinancialIncome(BigDecimal.valueOf(300_000))
                .liquidAsset(BigDecimal.valueOf(7_000_000))
                .medicalPreparedAsset(BigDecimal.valueOf(5_000_000))
                .expectedAnnualMedicalExpense(BigDecimal.valueOf(6_000_000))
                .build();

        assertThatThrownBy(() -> calculator.calculate(input))
                .isInstanceOf(BaseException.class);
    }
}
