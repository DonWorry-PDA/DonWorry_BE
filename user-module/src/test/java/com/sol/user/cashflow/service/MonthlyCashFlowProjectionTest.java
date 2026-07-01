package com.sol.user.cashflow.service;

import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonthlyCashFlowProjectionTest {

    private static final Long USER_ID = 1L;

    @Mock CashFlowEventRepository cashFlowEventRepository;
    @InjectMocks MonthlyCashFlowProjection projection;

    @Test
    void 시드월에_고정된_recurring_정기수입은_다음달로_투영된다() {
        // 6월(시드월)에 고정된 recurring 연금 이벤트. 핵심 버그: 이게 7월 집계에서 사라지면 안 된다.
        CashFlowEvent pension = event(LocalDate.of(2026, 6, 5), "PENSION", "INCOME",
                BigDecimal.valueOf(900_000), true);
        stubEvents(pension);

        ProjectedMonth july = projection.project(USER_ID, YearMonth.of(2026, 7));

        assertThat(july.sumEventType("PENSION")).isEqualByComparingTo("900000");
        assertThat(july.sumFlow("INCOME")).isEqualByComparingTo("900000");
    }

    @Test
    void recurring은_앵커월_이전_달에는_잡히지_않는다() {
        CashFlowEvent pension = event(LocalDate.of(2026, 6, 5), "PENSION", "INCOME",
                BigDecimal.valueOf(900_000), true);
        stubEvents(pension);

        ProjectedMonth may = projection.project(USER_ID, YearMonth.of(2026, 5));

        assertThat(may.sumEventType("PENSION")).isEqualByComparingTo("0");
    }

    @Test
    void 비반복_이벤트는_그_달에만_잡힌다() {
        CashFlowEvent expense = event(LocalDate.of(2026, 7, 10), "MAINTENANCE", "EXPENSE",
                BigDecimal.valueOf(178_200), false);
        stubEvents(expense);

        assertThat(projection.project(USER_ID, YearMonth.of(2026, 7)).sumFlow("EXPENSE"))
                .isEqualByComparingTo("178200");
        assertThat(projection.project(USER_ID, YearMonth.of(2026, 8)).sumFlow("EXPENSE"))
                .isEqualByComparingTo("0");
    }

    @Test
    void 주식매매는_수입지출_합계에서_제외된다() {
        CashFlowEvent stockSell = event(LocalDate.of(2026, 7, 12), "STOCK_SELL", "INCOME",
                BigDecimal.valueOf(500_000), false);
        CashFlowEvent interest = event(LocalDate.of(2026, 7, 30), "INTEREST", "INCOME",
                BigDecimal.valueOf(47_850), true);
        stubEvents(stockSell, interest);

        ProjectedMonth july = projection.project(USER_ID, YearMonth.of(2026, 7));

        // STOCK_SELL(INCOME)은 제외 → 이자만 남는다.
        assertThat(july.sumFlow("INCOME")).isEqualByComparingTo("47850");
    }

    private void stubEvents(CashFlowEvent... events) {
        when(cashFlowEventRepository.findCalendarEvents(eq(USER_ID), any(), any()))
                .thenReturn(List.of(events));
    }

    private CashFlowEvent event(LocalDate date, String type, String flowType,
                                BigDecimal amount, boolean recurring) {
        CashFlowEvent event = mock(CashFlowEvent.class);
        when(event.getEventDate()).thenReturn(date);
        when(event.getEventType()).thenReturn(type);
        when(event.getFlowType()).thenReturn(flowType);
        when(event.getAmount()).thenReturn(amount);
        when(event.getRecurring()).thenReturn(recurring);
        return event;
    }
}
