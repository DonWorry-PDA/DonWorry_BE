package com.sol.user.cashflow.service;

import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "이번 달 현금흐름"의 <b>단일 투영 소스</b>. recurring 이벤트는 시드월에 고정된 eventDate로 저장되므로,
 * 그 달을 조회월로 <b>투영</b>해야 시드월 다음 달부터 연금·이자가 사라지지 않는다.
 *
 * <p>예전엔 캘린더만 투영({@code resolveCashFlowDate})하고, 리포트·홈·진단은 {@code eventDate BETWEEN}
 * 날짜창 필터만 써서 시드월 다음 달부터 정기수입(연금·이자)이 통째로 0이 됐다. 이제 날짜 투영 규칙을
 * 여기 한곳에 두고 캘린더도 {@link #resolveDate}를 호출한다 — 투영 규칙이 두 곳에서 갈라질 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyCashFlowProjection {

    /** 주식 매수·매도는 투자 거래로, 수입·지출 합계에서 제외한다(기존 flowType 집계 쿼리와 동일). */
    private static final Set<String> INVESTMENT_EVENT_TYPES = Set.of("STOCK_BUY", "STOCK_SELL");

    private final CashFlowEventRepository cashFlowEventRepository;

    public ProjectedMonth project(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<ProjectedEvent> events = new ArrayList<>();
        for (CashFlowEvent event : cashFlowEventRepository.findCalendarEvents(userId, from, to)) {
            LocalDate date = resolveDate(event, ym);
            if (date == null) {
                continue;
            }
            events.add(new ProjectedEvent(
                    event.getEventType(), event.getFlowType(), nz(event.getAmount())));
        }
        return new ProjectedMonth(events);
    }

    /**
     * 이벤트를 조회월({@code targetMonth})의 날짜로 해석한다. 비반복은 그 달에 실제로 dated된 것만,
     * recurring은 시드 앵커월 이후이면 조회월의 같은 일(day)로 투영한다. 캘린더·집계가 공유하는 유일한 규칙.
     */
    public static LocalDate resolveDate(CashFlowEvent event, YearMonth targetMonth) {
        LocalDate originalDate = event.getEventDate();
        if (originalDate == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(event.getRecurring())) {
            return targetMonth.equals(YearMonth.from(originalDate)) ? originalDate : null;
        }
        YearMonth originalMonth = YearMonth.from(originalDate);
        if (targetMonth.isBefore(originalMonth)) {
            return null;
        }
        int day = Math.min(originalDate.getDayOfMonth(), targetMonth.lengthOfMonth());
        return targetMonth.atDay(day);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ProjectedEvent(String eventType, String flowType, BigDecimal amount) {
    }

    /** 조회월로 투영된 이벤트 묶음. eventType별·flowType별 합계를 제공한다. */
    public static final class ProjectedMonth {
        private final List<ProjectedEvent> events;

        public ProjectedMonth(List<ProjectedEvent> events) {
            this.events = events;
        }

        /** 특정 eventType(PENSION·INTEREST 등) 금액 합계. */
        public BigDecimal sumEventType(String eventType) {
            return events.stream()
                    .filter(e -> eventType.equals(e.eventType()))
                    .map(ProjectedEvent::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** 특정 flowType(INCOME·EXPENSE) 금액 합계. 주식 매수·매도는 제외한다. */
        public BigDecimal sumFlow(String flowType) {
            return events.stream()
                    .filter(e -> flowType.equals(e.flowType()))
                    .filter(e -> !INVESTMENT_EVENT_TYPES.contains(e.eventType()))
                    .map(ProjectedEvent::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
