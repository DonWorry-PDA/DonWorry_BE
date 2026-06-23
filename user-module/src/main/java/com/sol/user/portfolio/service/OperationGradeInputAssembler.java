package com.sol.user.portfolio.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.OperationGradeInput;
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
 * <p>자산 집계 규칙(A안): totalAsset = 전 계좌 잔액 합(BROKERAGE 잔액에 ETF 평가액이 이미 반영돼 holding 별도합산 안 함),
 * pensionSaving = 연금저축+IRP(55세 인출제약 트랙), availableFinancialAsset = totalAsset − pensionSaving(즉시 인출 가능분).
 * gender 미수집(합산 기대여명), essentialRatio·investmentPropensity는 정책 상수({@link PortfolioConstants}).
 */
@Component
@RequiredArgsConstructor
public class OperationGradeInputAssembler {

    /** 55세 인출제약이 걸린 연금 계좌 — 운용 모수에서 분리. */
    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("PENSION_SAVING", "IRP");
    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";
    /** 실손보험 유형. */
    private static final Set<String> LOSS_INSURANCE_TYPES = Set.of("INDEMNITY");
    /** 중대질병·장기요양 보험 유형. */
    private static final Set<String> MAJOR_ILLNESS_TYPES = Set.of("CANCER", "NURSING");

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PensionRepository pensionRepository;
    private final DebtRepository debtRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final UserGoalRepository userGoalRepository;
    private final SurveyService surveyService;

    @Transactional(readOnly = true)
    public OperationGradeInput assemble(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        if (user.getAge() == null) {
            // 온보딩으로 나이를 채우기 전이면 STEP1 기대여명 계산 불가
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        List<Account> accounts = accountRepository.findByUserUserId(userId);
        BigDecimal totalAsset = accounts.stream()
                .map(account -> nullToZero(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pensionSaving = accounts.stream()
                .filter(account -> PENSION_ACCOUNT_TYPES.contains(account.getAccountType()))
                .map(account -> nullToZero(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableFinancialAsset = totalAsset.subtract(pensionSaving).max(BigDecimal.ZERO);

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

        // 투자 스타일 설문(월급만들기) — 미응답이면 SURVEY_NOT_FOUND로 명확히 실패
        SurveyAnswerResponse survey = surveyService.get(userId);

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
                .investmentPropensity(PortfolioConstants.DEFAULT_PROPENSITY)
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
