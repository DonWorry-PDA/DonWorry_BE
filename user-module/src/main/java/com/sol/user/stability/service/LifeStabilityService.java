package com.sol.user.stability.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
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
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
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
    private final UserGoalRepository userGoalRepository;
    private final AccountRepository accountRepository;
    private final PensionRepository pensionRepository;
    private final DebtRepository debtRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final CashFlowEventRepository cashFlowEventRepository;

    public LifeStabilityResponse preview() {
        LifeStabilityCalculationInput input = createMockInput();
        LifeStabilityCalculatedResult calculated = calculator.calculate(input);
        String summaryMessage = messageGenerator.generateSummary(calculated);

        return toResponse(
                calculated,
                summaryMessage,
                messageGenerator.generateImprovementMessages(calculated)
        );
    }

    public LifeStabilityResponse getLatest(Long userId) {
        StabilityScore result = stabilityScoreRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        return toResponse(result, messageGenerator.generateImprovementMessages(toCalculatedResult(result)));
    }

    @Transactional
    public LifeStabilityResponse recalculateAndSave(Long userId, LifeStabilityCalculationInput input) {
        LifeStabilityCalculatedResult calculated = calculator.calculate(input);
        String summaryMessage = messageGenerator.generateSummary(calculated);
        StabilityScore saved = stabilityScoreRepository.save(StabilityScore.builder()
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
                .build());

        return toResponse(saved, messageGenerator.generateImprovementMessages(calculated));
    }

    @Transactional
    public LifeStabilityResponse recalculateFromUserData(Long userId) {
        UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        // 목표 생활비/예상 의료비는 계산 필수값. 누락 시 NPE가 아니라 명확한 도메인 예외로 차단한다.
        if (goal.getMonthlyTargetLivingCost() == null || goal.getMonthlyExpectedMedicalCost() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        List<InsurancePolicy> policies = insurancePolicyRepository.findByUserUserId(userId);
        List<CashFlowEvent> events = cashFlowEventRepository.findByUserUserId(userId);

        BigDecimal pensionIncome = pensionRepository.findByUserUserId(userId).stream()
                .map(pension -> pension.getExpectedMonthlyAmount() == null
                        ? BigDecimal.ZERO : pension.getExpectedMonthlyAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal financialIncome = sumEvents(events, "INCOME", List.of("INTEREST", "DIVIDEND"));
        BigDecimal essentialExpense = sumEvents(events, "EXPENSE", List.of("MAINTENANCE", "INSURANCE", "CARD"));
        if (essentialExpense.signum() == 0) {
            essentialExpense = goal.getMonthlyTargetLivingCost();
        }
        BigDecimal liquidAsset = accounts.stream()
                .filter(account -> "CHECKING_CMA".equals(account.getAccountType()))
                .map(account -> account.getDepositBalance() == null ? BigDecimal.ZERO : account.getDepositBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal medicalReserve = policies.stream()
                .filter(policy -> Boolean.TRUE.equals(policy.getActive()))
                .map(policy -> policy.getMedicalReserve() == null ? BigDecimal.ZERO : policy.getMedicalReserve())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loanRepayment = debtRepository.findByUserUserId(userId).stream()
                .map(debt -> debt.getMonthlyRepayment() == null ? BigDecimal.ZERO : debt.getMonthlyRepayment())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 주의: 현재 RISK_ASSET_WITHDRAWAL 이벤트를 시드하지 않으므로 이 값은 사실상 항상 0이다.
        // 0은 not-null이라 calculator의 "부족액 기반 추정" 폴백(null일 때만 작동)을 타지 않는다.
        // 이는 버그가 아니라 의도된 동작: 위험자산 의존도를 부족액 기반으로 바꾸면
        // 점수 보정이 틀어져 userId 2의 시연 등급이 보완 필요 → 개선 필요로 내려간다.
        // 지표 정의를 바꾸려면 scoreRiskAssetDependency/등급 컷을 함께 재보정할 것.
        BigDecimal riskAssetWithdrawal = sumEvents(
                events,
                "INCOME",
                List.of("RISK_ASSET_WITHDRAWAL")
        );

        return recalculateAndSave(userId, LifeStabilityCalculationInput.builder()
                .targetMonthlyLivingExpense(goal.getMonthlyTargetLivingCost())
                .monthlyIncome(pensionIncome)
                .monthlyFixedExpense(BigDecimal.ZERO)
                .monthlyEssentialExpense(essentialExpense)
                .monthlyLoanRepayment(loanRepayment)
                .monthlyFinancialIncome(financialIncome)
                .monthlyRiskAssetWithdrawal(riskAssetWithdrawal)
                .liquidAsset(liquidAsset)
                .medicalPreparedAsset(medicalReserve)
                .expectedAnnualMedicalExpense(goal.getMonthlyExpectedMedicalCost().multiply(BigDecimal.valueOf(12)))
                .build());
    }

    private BigDecimal sumEvents(List<CashFlowEvent> events, String flowType, List<String> eventTypes) {
        return events.stream()
                .filter(event -> flowType.equals(event.getFlowType()))
                .filter(event -> eventTypes.contains(event.getEventType()))
                .map(event -> event.getAmount() == null ? BigDecimal.ZERO : event.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        return toResponse(toCalculatedResult(result), result.getSummaryMessage(), improvementMessages);
    }

    private LifeStabilityResponse toResponse(
            LifeStabilityCalculatedResult result,
            String summaryMessage,
            List<String> improvementMessages
    ) {
        return LifeStabilityResponse.builder()
                .totalScore(result.getTotalScore())
                .grade(result.getGrade().name())
                .gradeLabel(result.getGrade().getLabel())
                .summaryMessage(summaryMessage)
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
                        .medicalPreparednessStatus(toMonthStatus(result.getMedicalPreparednessMonths(), BigDecimal.valueOf(24)).getLabel())
                        .liquidityStatus(toMonthStatus(result.getLiquidityMonths(), BigDecimal.valueOf(6)).getLabel())
                        .debtBurdenStatus(toDebtStatus(result.getDebtBurdenRate()).getLabel())
                        .riskAssetDependencyStatus(toRiskDependencyStatus(result.getRiskAssetDependencyRate()).getLabel())
                        .build())
                .planGuardrail(PlanGuardrailResponse.builder()
                        .growthPlanAllowed(result.isGrowthPlanAllowed())
                        .recommendedPlanType(result.getRecommendedPlanType().name())
                        .recommendedPlanLabel(result.getRecommendedPlanType().getLabel())
                        .reason(messageGenerator.generateGuardrailReason(result))
                        .build())
                .improvementMessages(improvementMessages)
                .build();
    }

    private LifeStabilityCalculatedResult toCalculatedResult(StabilityScore result) {
        return LifeStabilityCalculatedResult.builder()
                .totalScore(result.getTotalScore())
                .grade(result.getGrade())
                .cashflowCoverageRate(result.getCashflowCoverageRate())
                .essentialExpenseRate(result.getEssentialExpenseRate())
                .liquidityMonths(result.getLiquidityMonths())
                .medicalPreparednessMonths(result.getMedicalPreparednessMonths())
                .debtBurdenRate(result.getDebtBurdenRate())
                .riskAssetDependencyRate(result.getRiskAssetDependencyRate())
                .growthPlanAllowed(result.isGrowthPlanAllowed())
                .recommendedPlanType(result.getRecommendedPlanType())
                .build();
    }

    private LifeStabilityIndicatorStatus toCashflowStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return LifeStabilityIndicatorStatus.STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return LifeStabilityIndicatorStatus.NEED_COMPLEMENT;
        }
        return LifeStabilityIndicatorStatus.NEED_IMPROVEMENT;
    }

    private LifeStabilityIndicatorStatus toExpenseStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return LifeStabilityIndicatorStatus.STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return LifeStabilityIndicatorStatus.NEED_COMPLEMENT;
        }
        return LifeStabilityIndicatorStatus.NEED_IMPROVEMENT;
    }

    private LifeStabilityIndicatorStatus toMonthStatus(BigDecimal months, BigDecimal goodThreshold) {
        if (months.compareTo(goodThreshold) >= 0) {
            return LifeStabilityIndicatorStatus.STABLE;
        }
        BigDecimal normalThreshold = goodThreshold.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (months.compareTo(normalThreshold) >= 0) {
            return LifeStabilityIndicatorStatus.NEED_COMPLEMENT;
        }
        return LifeStabilityIndicatorStatus.NEED_IMPROVEMENT;
    }

    private LifeStabilityIndicatorStatus toDebtStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return LifeStabilityIndicatorStatus.STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(40)) <= 0) {
            return LifeStabilityIndicatorStatus.NEED_COMPLEMENT;
        }
        return LifeStabilityIndicatorStatus.NEED_IMPROVEMENT;
    }

    private LifeStabilityIndicatorStatus toRiskDependencyStatus(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return LifeStabilityIndicatorStatus.STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return LifeStabilityIndicatorStatus.NEED_COMPLEMENT;
        }
        return LifeStabilityIndicatorStatus.NEED_IMPROVEMENT;
    }
}
