package com.sol.user.portfolio.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.service.SurveyService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * userId → OperationGradeInput(STEP1~4 입력) 조립. OperationGradeService·PortfolioRecommendationService 공용.
 *
 * <p>자산 집계는 {@link AssetAggregator}에 위임(예수금만 계약): totalAsset = 예수금 + 비STOCK 보유종목 평가액
 * (개별주식 제외 — 청산 전제라 월급 재원 아님), pensionSaving = 연금저축+IRP(55세 인출제약 트랙),
 * availableFinancialAsset = totalAsset − pensionSaving(즉시 인출 가능분).
 * gender 미수집(합산 기대여명), essentialRatio·investmentPropensity는 정책 상수({@link PortfolioConstants}).
 */
@Component
@RequiredArgsConstructor
public class OperationGradeInputAssembler {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";
    /** 실손보험 유형. */
    private static final Set<String> LOSS_INSURANCE_TYPES = Set.of("INDEMNITY");
    /** 중대질병·장기요양 보험 유형. */
    private static final Set<String> MAJOR_ILLNESS_TYPES = Set.of("CANCER", "NURSING");

    private final UserRepository userRepository;
    private final AssetAggregator assetAggregator;
    private final PensionRepository pensionRepository;
    private final DebtRepository debtRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final UserGoalRepository userGoalRepository;
    private final SurveyService surveyService;

    @Transactional(readOnly = true)
    public OperationGradeInput assemble(Long userId) {
        // 투자 스타일 설문(월급만들기) — 미응답이면 SURVEY_NOT_FOUND로 명확히 실패
        return assemble(userId, surveyService.get(userId));
    }

    /** 설문을 이미 조회한 추천 파이프라인이 중복 조회 없이 재사용하도록 받는 오버로드. */
    @Transactional(readOnly = true)
    public OperationGradeInput assemble(Long userId, SurveyAnswerResponse survey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        if (user.getAge() == null) {
            // 온보딩으로 나이를 채우기 전이면 STEP1 기대여명 계산 불가
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        AssetBreakdown assets = assetAggregator.aggregate(userId);
        BigDecimal totalAsset = assets.operatingTotal();
        // 55세 제약분 = 연금 예수금 + 연금 보유종목. 계산기(OperationGradeCalculator)가
        // availableAsset = totalAsset − pensionSaving 으로 재계산하므로 연금 종목까지 포함해 넘긴다.
        BigDecimal pensionSaving = assets.restrictedPension();
        BigDecimal availableFinancialAsset = assets.availableFinancialAsset();

        UserGoal goal = userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        BigDecimal monthlyNationalPension = pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO);

        List<InsurancePolicy> policies = insurancePolicyRepository.findByUserUserId(userId);
        boolean hasLossInsurance = hasActivePolicy(policies, LOSS_INSURANCE_TYPES);
        boolean hasMajorIllnessInsurance = hasActivePolicy(policies, MAJOR_ILLNESS_TYPES);

        BigDecimal monthlyLoanRepayment = debtRepository.findByUserUserId(userId).stream()
                .map(debt -> nullToZero(debt.getMonthlyRepayment()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 증권 적합성진단(KYC) 성향 — 연동 전이면 null이라 정책 기본값으로 대체. 권유가능등급 필터(#118)의 입력.
        InvestmentPropensity propensity = user.getInvestmentPropensity() != null
                ? user.getInvestmentPropensity()
                : PortfolioConstants.DEFAULT_PROPENSITY;

        return OperationGradeInput.builder()
                .age(user.getAge())
                .totalAsset(totalAsset)
                .pensionSaving(pensionSaving)
                .targetMonthlyLivingCost(goal.getMonthlyTargetLivingCost())
                .essentialRatio(PortfolioConstants.ESSENTIAL_RATIO)
                .monthlyNationalPension(monthlyNationalPension)
                .availableFinancialAsset(availableFinancialAsset)
                .hasLossInsurance(hasLossInsurance)
                .hasMajorIllnessInsurance(hasMajorIllnessInsurance)
                .monthlyLoanRepayment(monthlyLoanRepayment)
                .q1(survey.getQ1())
                .q2(survey.getQ2())
                .investmentPropensity(propensity)
                .build();
    }

    private boolean hasActivePolicy(List<InsurancePolicy> policies, Set<String> types) {
        return policies.stream()
                .anyMatch(policy -> Boolean.TRUE.equals(policy.getActive())
                        && types.contains(policy.getInsuranceType()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
