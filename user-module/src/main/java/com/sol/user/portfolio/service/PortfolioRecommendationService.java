package com.sol.user.portfolio.service;

import com.sol.user.portfolio.calculator.AlphaCoverageCalculator;
import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.calculator.PortfolioAllocationCalculator;
import com.sol.user.portfolio.dto.AllocationInput;
import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.CoverageInput;
import com.sol.user.portfolio.dto.CoverageResult;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResult;
import com.sol.user.portfolio.dto.PlanCoverage;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.Gender;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 은퇴 포트폴리오 추천 오케스트레이터 — STEP1~4(운용등급) 재사용 → 풀조회 → STEP5(배분) → STEP6(충족률) → 응답 조립.
 */
@Service
@RequiredArgsConstructor
public class PortfolioRecommendationService {

    private static final String Q3_REFERENCE_LABEL = "안정안 기준 예시";

    private final OperationGradeCalculator operationGradeCalculator;
    private final PortfolioAllocationCalculator allocationCalculator;
    private final AlphaCoverageCalculator coverageCalculator;
    private final EtfPoolProvider etfPoolProvider;

    public RecommendationResponse recommend(Long userId) {
        OperationGradeInput input = createMockInput();

        // STEP1~4 재사용
        OperationGradeResult grade = operationGradeCalculator.calculate(input);

        // STEP5 — 풀조회 후 배분
        List<EtfInfo> pool = etfPoolProvider.getPool();
        AllocationResult allocation = allocationCalculator.calculate(toAllocationInput(grade, input, pool));

        // STEP6 — α충족률·소진모델
        CoverageResult coverage = coverageCalculator.calculate(toCoverageInput(allocation, grade, input));

        return toResponse(allocation, coverage);
    }

    private AllocationInput toAllocationInput(OperationGradeResult grade, OperationGradeInput input, List<EtfInfo> pool) {
        return AllocationInput.builder()
                .finalGrade(grade.getFinalGrade())
                .surplus(grade.getSurplus())
                .floorAsset(grade.getFloorAsset())
                .totalAsset(input.totalAsset())
                .pensionSaving(input.pensionSaving())
                .propensity(input.investmentPropensity())
                .shortTermBucket(MOCK_SHORT_TERM_BUCKET)
                .pool(pool)
                .build();
    }

    private CoverageInput toCoverageInput(AllocationResult allocation, OperationGradeResult grade, OperationGradeInput input) {
        return CoverageInput.builder()
                .allocation(allocation)
                .q3(MOCK_Q3)
                .age(input.age())
                .remainingYears(grade.getRemainingYears())
                .targetLivingCost(input.targetMonthlyLivingCost())
                .monthlyNationalPension(input.monthlyNationalPension())
                .otherRegularIncome(MOCK_OTHER_REGULAR_INCOME)
                .floorAsset(grade.getFloorAsset())
                .pensionSaving(input.pensionSaving())
                .build();
    }

    private RecommendationResponse toResponse(AllocationResult allocation, CoverageResult coverage) {
        Map<PlanType, PlanCoverage> coverageByType = coverage.getPlanCoverages().stream()
                .collect(Collectors.toMap(PlanCoverage::getType, Function.identity()));

        List<PlanResponse> plans = allocation.getPlans().stream()
                .map(plan -> PlanResponse.of(plan, coverageByType.get(plan.getType())))
                .toList();

        String q3Label = coverage.getQ3Scenarios().isEmpty() ? null : Q3_REFERENCE_LABEL;

        return RecommendationResponse.builder()
                .track(coverage.getTrack())   // STEP6가 확정한 최종 트랙
                .alpha(coverage.getAlpha())
                .band(coverage.getBand())
                .plans(plans)
                .q3ReferenceLabel(q3Label)
                .q3Scenarios(coverage.getQ3Scenarios())
                .build();
    }

    // TODO: 마이데이터·설문 연동 후 실제 데이터로 교체
    private static final int MOCK_Q3 = 1;
    private static final BigDecimal MOCK_OTHER_REGULAR_INCOME = BigDecimal.ZERO;
    private static final BigDecimal MOCK_SHORT_TERM_BUCKET = BigDecimal.ZERO; // 0이면 계산기가 여유분×0.10으로 기본 적용

    private OperationGradeInput createMockInput() {
        return OperationGradeInput.builder()
                .age(65)
                .gender(Gender.MALE)
                .totalAsset(BigDecimal.valueOf(600_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000))
                .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                .essentialRatio(new BigDecimal("0.72"))
                .monthlyNationalPension(BigDecimal.valueOf(1_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(500_000_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2)
                .q2(1)
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }
}
