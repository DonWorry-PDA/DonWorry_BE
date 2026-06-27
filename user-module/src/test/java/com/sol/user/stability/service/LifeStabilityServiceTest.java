package com.sol.user.stability.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.service.EtfDividendCalculator;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LifeStabilityServiceTest {

    private StabilityScoreRepository stabilityScoreRepository;
    private UserGoalRepository userGoalRepository;
    private AccountRepository accountRepository;
    private PensionRepository pensionRepository;
    private DebtRepository debtRepository;
    private InsurancePolicyRepository insurancePolicyRepository;
    private CashFlowEventRepository cashFlowEventRepository;
    private EtfDividendCalculator etfDividendCalculator;
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
        etfDividendCalculator = mock(EtfDividendCalculator.class);
        // 배당은 보유ETF 기반 단일 출처(#216). 기본 0, 충당률 검증 테스트에서 개별 stub.
        lenient().when(etfDividendCalculator.monthlyDividend(any())).thenReturn(BigDecimal.ZERO);
        service = new LifeStabilityService(
                new LifeStabilityCalculator(),
                new LifeStabilityMessageGenerator(),
                stabilityScoreRepository,
                userGoalRepository,
                accountRepository,
                pensionRepository,
                debtRepository,
                insurancePolicyRepository,
                cashFlowEventRepository,
                etfDividendCalculator
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
                event(user, "MAINTENANCE", "EXPENSE", 180_000),
                event(user, "INSURANCE", "EXPENSE", 200_000),
                event(user, "CARD", "EXPENSE", 1_400_000)
        ));
        // 배당은 보유ETF 기반(#216) — financialIncome = 이자 48,000 + 배당 100,000
        when(etfDividendCalculator.monthlyDividend(1L)).thenReturn(money(100_000));
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

    @Test
    void recalculateFromUserDataIfReadySkipsWhenOnboardingNotDone() {
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L))
                .thenReturn(Optional.empty());

        service.recalculateFromUserDataIfReady(1L);

        verify(stabilityScoreRepository, never()).save(any());
    }

    @Test
    void recalculateFromUserDataIfReadySavesWhenOnboardingDone() {
        User user = mock(User.class);
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L))
                .thenReturn(Optional.of(new UserGoal(user, money(2_200_000), money(350_000), LocalDateTime.now())));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Pension(user, "NATIONAL", money(1_150_000), false, 65)
        ));
        when(debtRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(insurancePolicyRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(cashFlowEventRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(stabilityScoreRepository.save(any(StabilityScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recalculateFromUserDataIfReady(1L);

        verify(stabilityScoreRepository).save(any(StabilityScore.class));
    }

    @Test
    void scopesCashflowAggregationToLatestMonthIgnoringPastMonths() {
        // #169 회귀 방지: 6개월치(현재월 + 과거월)가 저장돼 있어도 월정액 지표는 최근 1개월만 집계해야 한다.
        // 과거월을 합산하면 financialIncome·essentialExpense가 부풀려져 충당률이 59.00을 넘는다.
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
        LocalDate base = LocalDate.now();
        LocalDate thisMonth = base;
        LocalDate lastMonth = base.minusMonths(1);
        when(cashFlowEventRepository.findByUserUserId(1L)).thenReturn(List.of(
                dated(user, "INTEREST", "INCOME", 48_000, thisMonth),
                dated(user, "MAINTENANCE", "EXPENSE", 180_000, thisMonth),
                dated(user, "INSURANCE", "EXPENSE", 200_000, thisMonth),
                dated(user, "CARD", "EXPENSE", 1_400_000, thisMonth),
                // 과거월(동일 값) — 집계에서 제외되어야 한다
                dated(user, "INTEREST", "INCOME", 48_000, lastMonth),
                dated(user, "MAINTENANCE", "EXPENSE", 180_000, lastMonth),
                dated(user, "INSURANCE", "EXPENSE", 200_000, lastMonth),
                dated(user, "CARD", "EXPENSE", 1_400_000, lastMonth)
        ));
        // 배당은 보유ETF 기반(#216) — 월 변동 없음
        when(etfDividendCalculator.monthlyDividend(1L)).thenReturn(money(100_000));
        when(stabilityScoreRepository.save(any(StabilityScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LifeStabilityResponse response = service.recalculateFromUserData(1L);

        // 단일월 기준 기대치와 동일해야 한다(과거월을 합산하면 59.00을 초과).
        assertThat(response.metrics().cashflowCoverageRate()).isEqualByComparingTo("59.00");
        assertThat(response.grade()).isEqualTo("NEED_COMPLEMENT");
    }

    @Test
    void includesEventsWithoutEventDateInAggregation() {
        // #169 리뷰 반영: eventDate가 null인 행은 월 스코핑에서 조용히 제외되지 않고 집계에 포함돼야 한다.
        User user = mock(User.class);
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L))
                .thenReturn(Optional.of(new UserGoal(user, money(2_200_000), money(350_000), LocalDateTime.now())));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Account(user, "CMA", "신한은행", "MOCK-1", money(18_000_000), true)
        ));
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Pension(user, "NATIONAL", money(1_150_000), false, 65)
        ));
        when(debtRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(insurancePolicyRepository.findByUserUserId(1L)).thenReturn(List.of(
                new InsurancePolicy(user, "신한라이프", "INDEMNITY", money(70_000), true, money(1_400_000))
        ));
        when(cashFlowEventRepository.findByUserUserId(1L)).thenReturn(List.of(
                dated(user, "INTEREST", "INCOME", 48_000, LocalDate.now()),
                dated(user, "INTEREST", "INCOME", 100_000, null)   // 날짜 미상 — 집계에 포함돼야 함
        ));
        when(stabilityScoreRepository.save(any(StabilityScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LifeStabilityResponse response = service.recalculateFromUserData(1L);

        // financialIncome = 48,000 + 100,000(null 포함) → securedCashflow 1,298,000 / 2,200,000 = 59.00.
        // null 행을 제외하면 54.45로 떨어진다.
        assertThat(response.metrics().cashflowCoverageRate()).isEqualByComparingTo("59.00");
    }

    private CashFlowEvent dated(User user, String eventType, String flowType, long amount, LocalDate date) {
        return new CashFlowEvent(
                user, date, eventType, eventType, money(amount), flowType,
                "SCHEDULED", true, "MYDATA_MOCK"
        );
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
