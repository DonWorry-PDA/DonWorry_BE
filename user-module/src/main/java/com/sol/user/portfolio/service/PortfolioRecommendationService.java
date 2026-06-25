package com.sol.user.portfolio.service;

import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
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
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.mapper.PortfolioRecommendationMapper;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 은퇴 포트폴리오 추천 오케스트레이터 — STEP1~4(운용등급) 재사용 → 풀조회 → STEP5(배분) → STEP6(충족률) → 응답 조립.
 */
@Service
@RequiredArgsConstructor
public class PortfolioRecommendationService {

    private final OperationGradeCalculator operationGradeCalculator;
    private final PortfolioAllocationCalculator allocationCalculator;
    private final AlphaCoverageCalculator coverageCalculator;
    private final EtfPoolProvider etfPoolProvider;
    private final PortfolioRecommendationMapper recommendationMapper;
    private final OperationGradeInputAssembler inputAssembler;
    private final SurveyService surveyService;
    private final CashFlowDiagnosisService cashFlowDiagnosisService;
    private final HoldingRepository holdingRepository;

    public RecommendationResponse recommend(Long userId) {
        // 설문 1회 조회 — q1/q2(STEP1~4 운용등급)는 assembler가, q3(STEP6 소진모델)는 여기서 재사용
        SurveyAnswerResponse survey = surveyService.get(userId);
        OperationGradeInput input = inputAssembler.assemble(userId, survey);
        int q3 = survey.getQ3();

        // STEP1~4 재사용
        OperationGradeResult grade = operationGradeCalculator.calculate(input);

        // STEP5 — 풀조회 후 배분
        List<EtfInfo> pool = etfPoolProvider.getPool();
        AllocationResult allocation = allocationCalculator.calculate(toAllocationInput(grade, input, pool));

        // STEP6 — α충족률·소진모델
        CoverageResult coverage = coverageCalculator.calculate(toCoverageInput(allocation, grade, input, q3));

        // BROKERAGE 계좌 보유종목 평가액 — 매수 실행 대상 계좌만 차감 (IRP 등 타 계좌 제외)
        Map<Long, BigDecimal> existingEvalByProductId = holdingRepository
                .findHoldingsWithAccountTypeByUserId(userId).stream()
                .filter(h -> "BROKERAGE".equals(h.getAccountType()))
                .collect(Collectors.toMap(
                        HoldingWithProduct::getProductId,
                        h -> h.getEvaluationAmount() == null ? BigDecimal.ZERO : h.getEvaluationAmount(),
                        BigDecimal::add
                ));

        // 화면 비교용 before 값 (현재 현금흐름 충당률 59% 등)
        CashFlowDiagnosisResponse cashFlow = cashFlowDiagnosisService.diagnose(userId);

        return recommendationMapper.toResponse(allocation, coverage,
                cashFlow.getMonthlyCashFlow(), cashFlow.getTargetMonthlyLivingCost(),
                existingEvalByProductId);
    }

    private AllocationInput toAllocationInput(OperationGradeResult grade, OperationGradeInput input, List<EtfInfo> pool) {
        return AllocationInput.builder()
                .finalGrade(grade.getFinalGrade())
                .surplus(grade.getSurplus())
                .floorAsset(grade.getFloorAsset())
                .totalAsset(input.totalAsset())
                .pensionSaving(input.pensionSaving())
                .propensity(input.investmentPropensity())
                .shortTermBucket(SHORT_TERM_BUCKET)
                .pool(pool)
                .build();
    }

    private CoverageInput toCoverageInput(AllocationResult allocation, OperationGradeResult grade,
                                          OperationGradeInput input, int q3) {
        return CoverageInput.builder()
                .allocation(allocation)
                .q3(q3)
                .age(input.age())
                .remainingYears(grade.getRemainingYears())
                .targetLivingCost(input.targetMonthlyLivingCost())
                .monthlyNationalPension(input.monthlyNationalPension())
                .otherRegularIncome(OTHER_REGULAR_INCOME)
                .floorAsset(grade.getFloorAsset())
                .pensionSaving(input.pensionSaving())
                .build();
    }

    /** 기타 정기수입(임대·근로 등) — 현재 수집 채널이 없어 0. 데이터 소스 확보 시 교체. */
    private static final BigDecimal OTHER_REGULAR_INCOME = BigDecimal.ZERO;
    /** 단기버킷 선확보액 — 0이면 STEP5가 유동성안에 한해 여유분×0.10을 기본 적용. */
    private static final BigDecimal SHORT_TERM_BUCKET = BigDecimal.ZERO;
}
