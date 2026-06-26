package com.sol.user.portfolio.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.service.SurveyService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OperationGradeInputAssemblerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AssetAggregator assetAggregator = mock(AssetAggregator.class);
    private final SalaryAssetExclusionRepository salaryAssetExclusionRepository =
            mock(SalaryAssetExclusionRepository.class);
    private final SalaryAssetMapper salaryAssetMapper = new SalaryAssetMapper();
    private final PensionRepository pensionRepository = mock(PensionRepository.class);
    private final DebtRepository debtRepository = mock(DebtRepository.class);
    private final InsurancePolicyRepository insurancePolicyRepository = mock(InsurancePolicyRepository.class);
    private final UserGoalRepository userGoalRepository = mock(UserGoalRepository.class);
    private final SurveyService surveyService = mock(SurveyService.class);

    private final OperationGradeInputAssembler assembler = new OperationGradeInputAssembler(
            userRepository, assetAggregator, salaryAssetExclusionRepository, salaryAssetMapper,
            pensionRepository, debtRepository, insurancePolicyRepository, userGoalRepository, surveyService);

    // ── #118-B: 증권 적합성진단 성향(User.investmentPropensity) 배선 ──────────────────

    @Test
    void 유저_성향이_있으면_그_값으로_조립한다() {
        stubUser(InvestmentPropensity.STABLE);

        OperationGradeInput input = assembler.assemble(1L, survey());

        assertThat(input.investmentPropensity()).isEqualTo(InvestmentPropensity.STABLE);
    }

    @Test
    void 유저_성향이_null이면_정책_기본값_위험중립형으로_대체한다() {
        stubUser(null); // 증권 연동 전 — 성향 미수집

        OperationGradeInput input = assembler.assemble(1L, survey());

        assertThat(input.investmentPropensity()).isEqualTo(InvestmentPropensity.NEUTRAL);
    }

    // ── #148: 예수금만 계약 집계 규칙(주식 제외·연금 제약분 차감) ──────────────────────

    @Test
    void 총자산은_개별주식을_제외하고_가용자산은_연금_제약분을_차감한다() {
        User user = mock(User.class);
        given(user.getAge()).willReturn(65);
        given(user.getInvestmentPropensity()).willReturn(InvestmentPropensity.NEUTRAL);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // 예수금 1억(연금 예수금 2천 포함) + 비연금 비STOCK 5천 + 연금 비STOCK 3천 + 개별주식 4천
        given(assetAggregator.aggregate(eq(1L), any(), any())).willReturn(new AssetBreakdown(
                BigDecimal.valueOf(100_000_000),  // cash
                BigDecimal.valueOf(20_000_000),   // pensionCash
                BigDecimal.valueOf(50_000_000),   // nonStockHoldingValue
                BigDecimal.valueOf(30_000_000),   // pensionHoldingValue
                BigDecimal.valueOf(40_000_000))); // stockHoldingValue

        UserGoal goal = mock(UserGoal.class);
        given(goal.getMonthlyTargetLivingCost()).willReturn(BigDecimal.valueOf(3_000_000));
        given(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).willReturn(Optional.of(goal));
        given(pensionRepository.findMonthlyAmount(any(), any())).willReturn(Optional.of(BigDecimal.valueOf(1_000_000)));
        given(insurancePolicyRepository.findByUserUserId(1L)).willReturn(List.of());
        given(debtRepository.findByUserUserId(1L)).willReturn(List.of());

        OperationGradeInput input = assembler.assemble(1L, survey());

        // totalAsset = 예수금 1억 + 비연금 5천 + 연금 3천 = 1.8억 (개별주식 4천 제외)
        assertThat(input.totalAsset()).isEqualByComparingTo("180000000");
        // pensionSaving = 연금 예수금 2천 + 연금 종목 3천 = 5천 (55세 제약)
        assertThat(input.pensionSaving()).isEqualByComparingTo("50000000");
        // availableFinancialAsset = 1.8억 − 5천 = 1.3억 (연금 종목이 즉시가용에 새지 않음)
        assertThat(input.availableFinancialAsset()).isEqualByComparingTo("130000000");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void stubUser(InvestmentPropensity propensity) {
        User user = mock(User.class);
        given(user.getAge()).willReturn(65);
        given(user.getInvestmentPropensity()).willReturn(propensity);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // 증권 예수금 6억 + 종목 없음 → operatingTotal 6억, 연금 제약분 0
        given(assetAggregator.aggregate(eq(1L), any(), any())).willReturn(
                new AssetBreakdown(BigDecimal.valueOf(600_000_000), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        UserGoal goal = mock(UserGoal.class);
        given(goal.getMonthlyTargetLivingCost()).willReturn(BigDecimal.valueOf(3_000_000));
        given(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).willReturn(Optional.of(goal));

        given(pensionRepository.findMonthlyAmount(any(), any())).willReturn(Optional.of(BigDecimal.valueOf(1_000_000)));
        given(insurancePolicyRepository.findByUserUserId(1L)).willReturn(List.of());
        given(debtRepository.findByUserUserId(1L)).willReturn(List.of());
    }

    private SurveyAnswerResponse survey() {
        return SurveyAnswerResponse.builder().q1(2).q2(1).q3(1).build();
    }
}
