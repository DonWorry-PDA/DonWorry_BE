package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetScheduleResponse;
import com.sol.user.asset.dto.AssetScheduleResponse.AssetScheduleEvent;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetScheduleServiceTest {

    private static final Long USER_ID = 1L;

    // Fixed "today" for all tests: 2026-01-01
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);
    private static final int MONTHS = 3; // window: [2026-01-01, 2026-04-01)

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private DepositDetailClient depositDetailClient;

    private AssetScheduleService service;

    @BeforeEach
    void setUp() {
        service = new AssetScheduleService(
                holdingRepository,
                debtRepository,
                accountRepository,
                depositDetailClient
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ETF_DIVIDEND
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ETF 분배금: 다음 지급일이 윈도우 내에 있으면 이벤트가 생성된다")
    void etfDividend_nextPaymentInWindow_eventCreated() {
        // latestPaymentDate = 2025-12-15, interval = 1 month
        // next = 2026-01-15 → within [2026-01-01, 2026-04-01)
        HoldingDividendCalendarProjection proj = mockProjection(
                100L, "TIGER ETF", new BigDecimal("100"), new BigDecimal("420"),
                LocalDate.of(2025, 12, 15), 1
        );
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of(proj));
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).hasSize(3); // Jan, Feb, Mar
        AssetScheduleEvent first = response.getEvents().get(0);
        assertThat(first.getDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(first.getType()).isEqualTo("ETF_DIVIDEND");
        assertThat(first.getLabel()).isEqualTo("TIGER ETF 예상 분배금");
        // 420 * 100 = 42000
        assertThat(first.getAmount()).isEqualByComparingTo("42000");
        assertThat(first.isEstimated()).isTrue();
    }

    @Test
    @DisplayName("ETF 분배금: 지급일이 윈도우 밖이면 이벤트가 생성되지 않는다")
    void etfDividend_nextPaymentOutsideWindow_noEvent() {
        // latestPaymentDate = 2026-04-01, interval = 1 → next = 2026-05-01 (outside)
        HoldingDividendCalendarProjection proj = mockProjection(
                101L, "KODEX ETF", new BigDecimal("50"), new BigDecimal("100"),
                LocalDate.of(2026, 4, 1), 1
        );
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of(proj));
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("ETF 분배금: 필수 필드가 null이면 건너뛴다")
    void etfDividend_nullFields_skipped() {
        HoldingDividendCalendarProjection proj = mock(HoldingDividendCalendarProjection.class);
        when(proj.getProductId()).thenReturn(null); // null productId → skip

        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of(proj));
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("ETF 분배금: interval <= 0이면 건너뛴다")
    void etfDividend_invalidInterval_skipped() {
        HoldingDividendCalendarProjection proj = mock(HoldingDividendCalendarProjection.class);
        when(proj.getProductId()).thenReturn(102L);
        when(proj.getQuantity()).thenReturn(new BigDecimal("10"));
        when(proj.getAmountPerUnit()).thenReturn(new BigDecimal("100"));
        when(proj.getLatestPaymentDate()).thenReturn(LocalDate.of(2025, 12, 15));
        when(proj.getDistributionIntervalMonths()).thenReturn(0); // interval = 0 → skip

        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of(proj));
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPOSIT_INTEREST
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEPOSIT_INTEREST: 3개월 윈도우에서 매월 이자 이벤트가 생성된다")
    void depositInterest_monthlyInterestEventsGenerated() {
        // openedAt = 2026-01-20 → payment day = 20
        // window [2026-01-01, 2026-04-01): Jan 20, Feb 20, Mar 20
        // balance=12,000,000, interestRate=1.0 → 12,000,000 * 1.0 / 1200 = 10,000
        Account depositAccount = mockDepositAccount(200L, new BigDecimal("12000000"),
                "신한은행", LocalDate.of(2026, 1, 20));
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccount));
        given(depositDetailClient.fetchDepositDetails(List.of(200L)))
                .willReturn(Map.of(200L, new DepositDetailItem(200L, new BigDecimal("1.0"), 12)));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        List<AssetScheduleEvent> events = response.getEvents();
        assertThat(events).hasSize(3);

        assertThat(events.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(events.get(0).getType()).isEqualTo("DEPOSIT_INTEREST");
        assertThat(events.get(0).getLabel()).isEqualTo("신한은행 예금 이자");
        assertThat(events.get(0).getAmount()).isEqualByComparingTo("10000");
        assertThat(events.get(0).isEstimated()).isFalse();

        assertThat(events.get(1).getDate()).isEqualTo(LocalDate.of(2026, 2, 20));
        assertThat(events.get(2).getDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    @DisplayName("DEPOSIT_INTEREST: 지급일이 해당 월 마지막 날보다 크면 말일로 조정된다")
    void depositInterest_paymentDayCappedAtMonthEnd() {
        // openedAt = 2026-01-31 → payment day = 31
        // Feb has 28 days in 2026 → Feb payment date = 2026-02-28
        Account depositAccount = mockDepositAccount(201L, new BigDecimal("1000000"),
                "우리은행", LocalDate.of(2026, 1, 31));
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccount));
        given(depositDetailClient.fetchDepositDetails(List.of(201L)))
                .willReturn(Map.of(201L, new DepositDetailItem(201L, new BigDecimal("1.2"), 12)));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        List<AssetScheduleEvent> events = response.getEvents();
        // Jan 31, Feb 28, Mar 31
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(events.get(1).getDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(events.get(2).getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("DEPOSIT_INTEREST: productId가 없는 DEPOSIT 계좌는 건너뛴다")
    void depositInterest_noProductId_skipped() {
        Account noProductAccount = mock(Account.class);
        when(noProductAccount.getAccountType()).thenReturn("DEPOSIT");
        when(noProductAccount.getProductId()).thenReturn(null);

        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(noProductAccount));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("DEPOSIT_INTEREST: openedAt이 null이면 건너뛴다")
    void depositInterest_nullOpenedAt_skipped() {
        Account account = mock(Account.class);
        when(account.getAccountType()).thenReturn("DEPOSIT");
        when(account.getProductId()).thenReturn(300L);
        when(account.getOpenedAt()).thenReturn(null);

        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(account));
        // depositDetailClient.fetchDepositDetails is NOT called (account filtered out due to null openedAt)
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPOSIT_MATURITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEPOSIT_MATURITY: openedAt + maturityMonths가 윈도우 내이면 이벤트가 생성된다")
    void depositMaturity_withinWindow_eventCreated() {
        // openedAt = 2025-01-15, maturityMonths = 12 → maturityDate = 2026-01-15
        // window [2026-01-01, 2026-04-01) → included
        Account depositAccount = mockDepositAccount(202L, new BigDecimal("5000000"),
                "신한은행", LocalDate.of(2025, 1, 15));
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccount));
        given(depositDetailClient.fetchDepositDetails(List.of(202L)))
                .willReturn(Map.of(202L, new DepositDetailItem(202L, new BigDecimal("1.5"), 12)));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        // 3 DEPOSIT_INTEREST (Jan 15, Feb 15, Mar 15) + 1 DEPOSIT_MATURITY (Jan 15)
        List<AssetScheduleEvent> maturityEvents = response.getEvents().stream()
                .filter(e -> "DEPOSIT_MATURITY".equals(e.getType()))
                .toList();

        assertThat(maturityEvents).hasSize(1);
        AssetScheduleEvent maturityEvent = maturityEvents.get(0);
        assertThat(maturityEvent.getDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(maturityEvent.getLabel()).isEqualTo("신한은행 정기예금 만기");
        assertThat(maturityEvent.getAmount()).isEqualByComparingTo("5000000");
        assertThat(maturityEvent.isEstimated()).isFalse();
    }

    @Test
    @DisplayName("DEPOSIT_MATURITY: 만기일이 윈도우 밖이면 이벤트가 생성되지 않는다")
    void depositMaturity_outsideWindow_noEvent() {
        // openedAt = 2024-04-01, maturityMonths = 24 → maturityDate = 2026-04-01 (exclusive boundary)
        Account depositAccount = mockDepositAccount(203L, new BigDecimal("3000000"),
                "국민은행", LocalDate.of(2024, 4, 1));
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccount));
        given(depositDetailClient.fetchDepositDetails(List.of(203L)))
                .willReturn(Map.of(203L, new DepositDetailItem(203L, new BigDecimal("1.0"), 24)));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        List<AssetScheduleEvent> maturityEvents = response.getEvents().stream()
                .filter(e -> "DEPOSIT_MATURITY".equals(e.getType()))
                .toList();
        assertThat(maturityEvents).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEBT_MATURITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEBT_MATURITY: 만기일이 윈도우 내이면 이벤트가 생성된다")
    void debtMaturity_withinWindow_eventCreated() {
        Debt debt = mockDebt("신한은행", LocalDate.of(2026, 2, 28));
        LocalDate endExclusive = TODAY.plusMonths(MONTHS); // 2026-04-01
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                USER_ID, TODAY, endExclusive.minusDays(1)))
                .willReturn(List.of(debt));
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        List<AssetScheduleEvent> debtEvents = response.getEvents().stream()
                .filter(e -> "DEBT_MATURITY".equals(e.getType()))
                .toList();

        assertThat(debtEvents).hasSize(1);
        AssetScheduleEvent event = debtEvents.get(0);
        assertThat(event.getDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(event.getType()).isEqualTo("DEBT_MATURITY");
        assertThat(event.getLabel()).isEqualTo("신한은행 대출 만기");
        assertThat(event.getAmount()).isNull();
        assertThat(event.isEstimated()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sorting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이벤트들은 날짜 오름차순으로 정렬된다")
    void events_sortedByDateAscending() {
        // ETF: 2026-01-15
        HoldingDividendCalendarProjection proj = mockProjection(
                100L, "TIGER ETF", new BigDecimal("10"), new BigDecimal("100"),
                LocalDate.of(2025, 12, 15), 3 // interval=3: next = 2026-03-15
        );
        // DEBT: 2026-02-01
        Debt debt = mockDebt("우리은행", LocalDate.of(2026, 2, 1));
        LocalDate endExclusive = TODAY.plusMonths(MONTHS);
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of(proj));
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                USER_ID, TODAY, endExclusive.minusDays(1)))
                .willReturn(List.of(debt));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        List<AssetScheduleEvent> events = response.getEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getDate()).isBeforeOrEqualTo(events.get(1).getDate());
        // ETF 2026-03-15, DEBT 2026-02-01 → DEBT first
        assertThat(events.get(0).getType()).isEqualTo("DEBT_MATURITY");
        assertThat(events.get(1).getType()).isEqualTo("ETF_DIVIDEND");
    }

    @Test
    @DisplayName("모든 소스가 없으면 빈 이벤트 목록을 반환한다")
    void noSources_emptyEventList() {
        given(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .willReturn(List.of());
        given(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
                eq(USER_ID), any(), any()))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());

        AssetScheduleResponse response = service.getSchedule(USER_ID, MONTHS, TODAY);

        assertThat(response.getEvents()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HoldingDividendCalendarProjection mockProjection(
            Long productId, String productName, BigDecimal quantity,
            BigDecimal amountPerUnit, LocalDate latestPaymentDate, int intervalMonths) {
        HoldingDividendCalendarProjection proj = mock(HoldingDividendCalendarProjection.class);
        when(proj.getProductId()).thenReturn(productId);
        when(proj.getProductName()).thenReturn(productName);
        when(proj.getQuantity()).thenReturn(quantity);
        when(proj.getAmountPerUnit()).thenReturn(amountPerUnit);
        when(proj.getLatestPaymentDate()).thenReturn(latestPaymentDate);
        when(proj.getDistributionIntervalMonths()).thenReturn(intervalMonths);
        return proj;
    }

    private Account mockDepositAccount(Long productId, BigDecimal balance,
                                       String institutionName, LocalDate openedAt) {
        Account account = mock(Account.class);
        when(account.getAccountType()).thenReturn("DEPOSIT");
        when(account.getProductId()).thenReturn(productId);
        when(account.getDepositBalance()).thenReturn(balance);
        when(account.getInstitutionName()).thenReturn(institutionName);
        when(account.getOpenedAt()).thenReturn(openedAt);
        return account;
    }

    private Debt mockDebt(String institutionName, LocalDate maturityDate) {
        Debt debt = mock(Debt.class);
        when(debt.getInstitutionName()).thenReturn(institutionName);
        when(debt.getMaturityDate()).thenReturn(maturityDate);
        return debt;
    }
}
