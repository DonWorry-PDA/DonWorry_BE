package com.sol.user.portfolio.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OperationGradeInputAssemblerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PensionRepository pensionRepository = mock(PensionRepository.class);
    private final DebtRepository debtRepository = mock(DebtRepository.class);
    private final InsurancePolicyRepository insurancePolicyRepository = mock(InsurancePolicyRepository.class);
    private final UserGoalRepository userGoalRepository = mock(UserGoalRepository.class);
    private final SurveyService surveyService = mock(SurveyService.class);

    private final OperationGradeInputAssembler assembler = new OperationGradeInputAssembler(
            userRepository, accountRepository, pensionRepository, debtRepository,
            insurancePolicyRepository, userGoalRepository, surveyService);

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

    // ── helper ────────────────────────────────────────────────────────────────

    private void stubUser(InvestmentPropensity propensity) {
        User user = mock(User.class);
        given(user.getAge()).willReturn(65);
        given(user.getInvestmentPropensity()).willReturn(propensity);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        Account account = mock(Account.class);
        given(account.getDepositBalance()).willReturn(BigDecimal.valueOf(600_000_000));
        given(account.getAccountType()).willReturn("BROKERAGE");
        given(accountRepository.findByUserUserId(1L)).willReturn(List.of(account));

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
