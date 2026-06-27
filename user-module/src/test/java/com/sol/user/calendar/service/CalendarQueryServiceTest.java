package com.sol.user.calendar.service;

import com.sol.common.exception.BaseException;
import com.sol.user.calendar.mapper.CalendarEventMapper;
import com.sol.user.calendar.type.CalendarEventCategory;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
import com.sol.user.holding.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarQueryServiceTest {

    @Mock
    private CashFlowEventRepository cashFlowEventRepository;

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private HoldingRepository holdingRepository;

    private CalendarQueryService service;

    @BeforeEach
    void setUp() {
        service = new CalendarQueryService(
                cashFlowEventRepository,
                debtRepository,
                holdingRepository,
                new CalendarEventMapper()
        );
    }

    @Test
    void projectsOneCashFlowSetIntoEventsAndSchedulesWithSignedAmount() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        CashFlowEvent event = cashFlow(
                10L,
                LocalDate.of(2026, 6, 15),
                "INSURANCE",
                "보험료",
                BigDecimal.valueOf(100_000),
                "EXPENSE",
                false
        );
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of(event));
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of());
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of());

        var result = service.getMonth(1L, 2026, 6);

        var calendarEvent = result.events().get("2026-06-15").get(0);
        var schedule = result.schedules().get("2026-06-15").get(0);
        assertThat(calendarEvent.category()).isEqualTo(CalendarEventCategory.PAYMENT);
        assertThat(calendarEvent.amountKrw()).isEqualByComparingTo("-100000");
        assertThat(schedule.category()).isEqualTo(calendarEvent.category());
        assertThat(schedule.amountKrw()).isEqualByComparingTo(calendarEvent.amountKrw());
        assertThat(schedule.title()).isEqualTo("보험료");
        assertThat(result.transactions()).isEmpty();
    }

    @Test
    void repeatsMonthEndCashFlowInsideRequestedMonthOnly() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        CashFlowEvent event = cashFlow(
                11L,
                LocalDate.of(2026, 5, 31),
                "PENSION",
                "국민연금 입금",
                BigDecimal.valueOf(1_500_000),
                "INCOME",
                true
        );
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of(event));
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of());
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of());

        var result = service.getMonth(1L, 2026, 6);

        assertThat(result.events()).containsOnlyKeys("2026-06-30");
        assertThat(result.events().get("2026-06-30").get(0).category())
                .isEqualTo(CalendarEventCategory.PENSION);
    }

    @Test
    void includesDebtMaturityWithNullableAmount() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        Debt debt = mock(Debt.class);
        when(debt.getId()).thenReturn(20L);
        when(debt.getMaturityDate()).thenReturn(LocalDate.of(2026, 6, 30));
        when(debt.getInstitutionName()).thenReturn("신한은행");
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of());
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of(debt));
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of());

        var result = service.getMonth(1L, 2026, 6);

        var event = result.events().get("2026-06-30").get(0);
        var schedule = result.schedules().get("2026-06-30").get(0);
        assertThat(event.category()).isEqualTo(CalendarEventCategory.MATURITY);
        assertThat(event.amountKrw()).isNull();
        assertThat(schedule.amountKrw()).isNull();
        assertThat(schedule.title()).isEqualTo("신한은행 대출 만기");
    }

    @Test
    void projectsQuarterlyEtfDividendIntoRequestedMonthUsingCurrentQuantity() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        HoldingDividendCalendarProjection input = mock(HoldingDividendCalendarProjection.class);
        when(input.getProductId()).thenReturn(100L);
        when(input.getProductName()).thenReturn("SOL 미국배당다우존스");
        when(input.getQuantity()).thenReturn(BigDecimal.TEN);
        when(input.getAmountPerUnit()).thenReturn(new BigDecimal("100.50"));
        when(input.getLatestPaymentDate()).thenReturn(LocalDate.of(2025, 12, 15));
        when(input.getDistributionIntervalMonths()).thenReturn(3);
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of());
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of());
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of(input));

        var result = service.getMonth(1L, 2026, 6);

        var event = result.events().get("2026-06-15").get(0);
        var schedule = result.schedules().get("2026-06-15").get(0);
        assertThat(event.category()).isEqualTo(CalendarEventCategory.DIVIDEND);
        assertThat(event.amountKrw()).isEqualByComparingTo("1005");
        assertThat(event.estimated()).isTrue();
        assertThat(schedule.estimated()).isTrue();
        assertThat(schedule.title()).isEqualTo("SOL 미국배당다우존스 예상 분배금");
    }

    @Test
    void showsActualDividendPaymentAsConfirmedOnPaymentDate() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        HoldingDividendPaymentProjection input = mock(HoldingDividendPaymentProjection.class);
        when(input.getProductId()).thenReturn(100L);
        when(input.getProductName()).thenReturn("SOL 미국배당다우존스");
        when(input.getQuantity()).thenReturn(BigDecimal.valueOf(5000));
        when(input.getAmountPerUnit()).thenReturn(new BigDecimal("33.00"));
        when(input.getPaymentDate()).thenReturn(LocalDate.of(2026, 6, 3));
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of());
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of());
        when(holdingRepository.findDividendPaymentsByUserId(1L, from, to)).thenReturn(List.of(input));
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of());

        var result = service.getMonth(1L, 2026, 6);

        var event = result.events().get("2026-06-03").get(0);
        var schedule = result.schedules().get("2026-06-03").get(0);
        assertThat(event.category()).isEqualTo(CalendarEventCategory.DIVIDEND);
        assertThat(event.amountKrw()).isEqualByComparingTo("165000"); // 33 × 5000
        assertThat(event.estimated()).isFalse();   // 확정(실지급)
        assertThat(schedule.estimated()).isFalse();
        assertThat(schedule.title()).isEqualTo("SOL 미국배당다우존스 분배금");
    }

    @Test
    void returnsEmptyMapsWhenMonthHasNoData() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        when(cashFlowEventRepository.findCalendarEvents(1L, from, to)).thenReturn(List.of());
        when(debtRepository.findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(1L, from, to))
                .thenReturn(List.of());
        when(holdingRepository.findDividendCalendarInputsByUserId(1L)).thenReturn(List.of());

        var result = service.getMonth(1L, 2026, 6);

        assertThat(result.events()).isEmpty();
        assertThat(result.schedules()).isEmpty();
        assertThat(result.transactions()).isEmpty();
        verify(cashFlowEventRepository).findCalendarEvents(1L, from, to);
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service.getMonth(1L, 2026, 13))
                .isInstanceOf(BaseException.class);
    }

    private CashFlowEvent cashFlow(
            Long id,
            LocalDate date,
            String type,
            String title,
            BigDecimal amount,
            String flowType,
            boolean recurring
    ) {
        CashFlowEvent event = mock(CashFlowEvent.class);
        when(event.getEventId()).thenReturn(id);
        when(event.getEventDate()).thenReturn(date);
        when(event.getEventType()).thenReturn(type);
        when(event.getTitle()).thenReturn(title);
        when(event.getAmount()).thenReturn(amount);
        when(event.getFlowType()).thenReturn(flowType);
        when(event.getRecurring()).thenReturn(recurring);
        return event;
    }
}
