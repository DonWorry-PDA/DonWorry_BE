package com.sol.user.stability.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.stability.calculator.LifeStabilityCalculator;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.entity.StabilityScore;
import com.sol.user.stability.message.LifeStabilityMessageGenerator;
import com.sol.user.stability.repository.StabilityScoreRepository;
import com.sol.user.user.entity.User;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LifeStabilityServiceTest {

    private StabilityScoreRepository stabilityScoreRepository;
    private UserGoalRepository userGoalRepository;
    private AccountRepository accountRepository;
    private PensionRepository pensionRepository;
    private DebtRepository debtRepository;
    private InsurancePolicyRepository insurancePolicyRepository;
    private CashFlowEventRepository cashFlowEventRepository;
    private LifeStabilityService service;

    @BeforeEach
    void setUp() {
        stabilityScoreRepository = mock(StabilityScoreRepository.class);
        userGoalRepository = mock(UserGoalRepository.class);
        accountRepository = mock(AccountRepository.class);
        pensionRepository = mock(PensionRepository.class);
        debtRepository = mock(DebtRepository.class);
        insurancePolicyRepository = mock(InsurancePolicyRepository.class);
        cashFlowEventRepository = mock(CashFlowEventRepository.class);
        service = new LifeStabilityService(
                new LifeStabilityCalculator(),
                new LifeStabilityMessageGenerator(),
                stabilityScoreRepository,
                userGoalRepository,
                accountRepository,
                pensionRepository,
                debtRepository,
                insurancePolicyRepository,
                cashFlowEventRepository
        );
    }

    @Test
    void recalculatesNeedComplementScenarioFromPersistedSourceData() {
        User user = mock(User.class);
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L))
                .thenReturn(Optional.of(new UserGoal(user, money(2_200_000), money(350_000), LocalDateTime.now())));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Account(user, "CMA", "신한은행", "MOCK-1", money(18_000_000), true)
        ));
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Pension(user, "NATIONAL", money(1_150_000), false, 65)
        ));
        when(debtRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Debt(user, "신한은행", "CREDIT_LOAN", money(30_000_000), money(300_000),
                        new BigDecimal("4.10"), LocalDate.now().plusYears(10))
        ));
        when(insurancePolicyRepository.findByUserUserId(1L)).thenReturn(List.of(
                new InsurancePolicy(user, "신한라이프", "INDEMNITY", money(70_000), true, money(1_400_000)),
                new InsurancePolicy(user, "신한라이프", "CANCER", money(70_000), true, money(1_400_000)),
                new InsurancePolicy(user, "신한라이프", "NURSING", money(60_000), true, money(1_400_000))
        ));
        when(cashFlowEventRepository.findByUserUserId(1L)).thenReturn(List.of(
                event(user, "INTEREST", "INCOME", 48_000),
                event(user, "DIVIDEND", "INCOME", 100_000),
                event(user, "MAINTENANCE", "EXPENSE", 180_000),
                event(user, "INSURANCE", "EXPENSE", 200_000),
                event(user, "CARD", "EXPENSE", 1_400_000)
        ));
        when(stabilityScoreRepository.save(any(StabilityScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LifeStabilityResponse response = service.recalculateFromUserData(1L);

        assertThat(response.grade()).isEqualTo("NEED_COMPLEMENT");
        assertThat(response.metrics().cashflowCoverageRate()).isEqualByComparingTo("59.00");
        assertThat(response.indicators().cashflowStatus()).isEqualTo("보완 필요");
        assertThat(response.indicators().debtBurdenStatus()).isEqualTo("안정");
        assertThat(response.indicators().medicalPreparednessStatus()).isEqualTo("보완 필요");
        assertThat(response.indicators().liquidityStatus()).isEqualTo("안정");
    }

    private CashFlowEvent event(User user, String eventType, String flowType, long amount) {
        return new CashFlowEvent(
                user, LocalDate.now(), eventType, eventType, money(amount), flowType,
                "SCHEDULED", true, "MYDATA_MOCK"
        );
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
