package com.sol.user.stability.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.stability.calculator.LifeStabilityCalculator;
import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.dto.LifeStabilityCalculationInput;
import com.sol.user.stability.dto.LifeStabilityIndicators;
import com.sol.user.stability.dto.LifeStabilityMetrics;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.dto.PlanGuardrailResponse;
import com.sol.user.stability.entity.StabilityScore;
import com.sol.user.stability.message.LifeStabilityMessageGenerator;
import com.sol.user.stability.repository.StabilityScoreRepository;
import com.sol.user.stability.type.LifeStabilityIndicatorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LifeStabilityService {

    private final LifeStabilityCalculator calculator;
    private final LifeStabilityMessageGenerator messageGenerator;
    private final StabilityScoreRepository stabilityScoreRepository;

    public LifeStabilityResponse recalculate(Long userId) {
        LifeStabilityCalculationInput input = createMockInput();
        LifeStabilityCalculatedResult calculated = calculator.calculate(input);
        String summaryMessage = messageGenerator.generateSummary(calculated);

        StabilityScore result = StabilityScore.builder()
                .userId(userId)
                .totalScore(calculated.getTotalScore())
                .grade(calculated.getGrade())
                .cashflowCoverageRate(calculated.getCashflowCoverageRate())
                .essentialExpenseRate(calculated.getEssentialExpenseRate())
                .medicalPreparednessMonths(calculated.getMedicalPreparednessMonths())
                .liquidityMonths(calculated.getLiquidityMonths())
                .debtBurdenRate(calculated.getDebtBurdenRate())
                .riskAssetDependencyRate(calculated.getRiskAssetDependencyRate())
                .growthPlanAllowed(calculated.isGrowthPlanAllowed())
                .recommendedPlanType(calculated.getRecommendedPlanType())
                .summaryMessage(summaryMessage)
                .build();

        return toResponse(result, messageGenerator.generateImprovementMessages(calculated));
    }

    public LifeStabilityResponse getLatest(Long userId) {
        StabilityScore result = stabilityScoreRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        return toResponse(result, messageGenerator.generateImprovementMessages(toCalculatedResult(result)));
    }

    private LifeStabilityCalculationInput createMockInput() {
        return LifeStabilityCalculationInput.builder()
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
    }

    private LifeStabilityResponse toResponse(StabilityScore result, List<String> improvementMessages) {
        return LifeStabilityResponse.builder()
                .grade(result.getGrade().name())
                .gradeLabel(result.getGrade().getLabel())
                .summaryMessage(result.getSummaryMessage())
                .metrics(LifeStabilityMetrics.builder()
                        .cashflowCoverageRate(result.getCashflowCoverageRate())
                        .essentialExpenseRate(result.getEssentialExpenseRate())
                        .medicalPreparednessMonths(result.getMedicalPreparednessMonths())
                        .liquidityMonths(result.getLiquidityMonths())
                        .debtBurdenRate(result.getDebtBurdenRate())
                        .riskAssetDependencyRate(result.getRiskAssetDependencyRate())
                        .build())
                .indicators(LifeStabilityIndicators.builder()
                        .cashflowStatus(toCashflowStatus(result.getCashflowCoverageRate()).getLabel())
                        .essentialExpenseStatus(toExpenseStatus(result.getEssentialExpenseRate()).getLabel())
                        .medicalPreparednessStatus(toMonthStatus(result.getMedicalPreparednessMonths(), BigDecimal.valueOf(12)).getLabel())
                        .liquidityStatus(toMonthStatus(result.getLiquidityMonths(), BigDecimal.valueOf(6)).getLabel())
                        .debtBurdenStatus(toDebtStatus(result.getDebtBurdenRate()).getLabel())
                        .riskAssetDependencyStatus(toRiskDependencyStatus(result.getRiskAssetDependencyRate()).getLabel())
                        .build())
                .planGuardrail(PlanGuardrailResponse.builder()
                        .growthPlanAllowed(result.isGrowthPlanAllowed())
                        .recommendedPlanType(result.getRecommendedPlanType().name())
                        .recommendedPlanLabel(result.getRecommendedPlanType().getLabel())
                        .reason(messageGenerator.generateGuardrailReason(toCalculatedResult(result)))
                        .build())
                .improvementMessages(improvementMessages)
                .build();
    }

    private LifeStabilityCalculatedResult toCalculatedResult(StabilityScore result) {
        return LifeStabilityCalculatedResult.builder()
                .grade(result.getGrade())
                .liquidityMonths(result.getLiquidityMonths())
                .medicalPreparednessMonths(result.getMedicalPreparednessMonths())
                .debtBurdenRate(result.getDebtBurdenRate())
                .riskAssetDependencyRate(result.getRiskAssetDependencyRate())
                .growthPlanAllowed(result.isGrowthPlanAllowed())
                .recommendedPlanType(result.getRecommendedPlanType())
                .build();
    }

    private LifeStabilityIndicatorStatus toCashflowStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return LifeStabilityIndicatorStatus.GOOD;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return LifeStabilityIndicatorStatus.NORMAL;
        }
        if (rate.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return LifeStabilityIndicatorStatus.NEED_CHECK;
        }
        return LifeStabilityIndicatorStatus.WEAK;
    }

    private LifeStabilityIndicatorStatus toExpenseStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return LifeStabilityIndicatorStatus.GOOD;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return LifeStabilityIndicatorStatus.NORMAL;
        }
        if (rate.compareTo(BigDecimal.valueOf(90)) <= 0) {
            return LifeStabilityIndicatorStatus.NEED_CHECK;
        }
        return LifeStabilityIndicatorStatus.WEAK;
    }

    private LifeStabilityIndicatorStatus toMonthStatus(BigDecimal months, BigDecimal goodThreshold) {
        if (months.compareTo(goodThreshold) >= 0) {
            return LifeStabilityIndicatorStatus.GOOD;
        }
        BigDecimal normalThreshold = goodThreshold.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (months.compareTo(normalThreshold) >= 0) {
            return LifeStabilityIndicatorStatus.NORMAL;
        }
        return LifeStabilityIndicatorStatus.WEAK;
    }

    private LifeStabilityIndicatorStatus toDebtStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(10)) <= 0) {
            return LifeStabilityIndicatorStatus.GOOD;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return LifeStabilityIndicatorStatus.NORMAL;
        }
        return LifeStabilityIndicatorStatus.WEAK;
    }

    private LifeStabilityIndicatorStatus toRiskDependencyStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return LifeStabilityIndicatorStatus.GOOD;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return LifeStabilityIndicatorStatus.NORMAL;
        }
        return LifeStabilityIndicatorStatus.WEAK;
    }
}
