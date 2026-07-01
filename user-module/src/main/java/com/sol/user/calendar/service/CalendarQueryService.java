package com.sol.user.calendar.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.calendar.dto.CalendarEventResponse;
import com.sol.user.calendar.dto.CalendarMonthResponse;
import com.sol.user.calendar.dto.CalendarScheduleResponse;
import com.sol.user.calendar.dto.CalendarTransactionResponse;
import com.sol.user.calendar.mapper.CalendarEventMapper;
import com.sol.user.calendar.type.CalendarEventCategory;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.service.DividendScheduleService;
import com.sol.user.holding.service.DividendScheduleService.DividendOccurrence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarQueryService {

    private final CashFlowEventRepository cashFlowEventRepository;
    private final DebtRepository debtRepository;
    private final DividendScheduleService dividendScheduleService;
    private final CalendarEventMapper calendarEventMapper;

    public CalendarMonthResponse getMonth(Long userId, int year, int month) {
        YearMonth targetMonth = toYearMonth(year, month);
        LocalDate from = targetMonth.atDay(1);
        LocalDate to = targetMonth.atEndOfMonth();

        List<CalendarItem> items = new ArrayList<>();
        addCashFlowItems(items, userId, targetMonth, from, to);
        addDebtMaturityItems(items, userId, from, to);
        addDividendItems(items, userId, from, to);
        items.sort(Comparator.comparing(CalendarItem::date).thenComparing(CalendarItem::id));

        Map<String, List<CalendarEventResponse>> events = new LinkedHashMap<>();
        Map<String, List<CalendarScheduleResponse>> schedules = new LinkedHashMap<>();
        Map<String, List<CalendarTransactionResponse>> transactions = new LinkedHashMap<>();
        for (CalendarItem item : items) {
            String date = item.date().toString();
            events.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(new CalendarEventResponse(
                            item.category(),
                            item.category().shortLabel(),
                            item.amountKrw(),
                            item.estimated()
                    ));
            if (item.transactional()) {
                transactions.computeIfAbsent(date, ignored -> new ArrayList<>())
                        .add(new CalendarTransactionResponse(
                                item.id(),
                                date,
                                item.category().value(),
                                item.title(),
                                item.amountKrw()
                        ));
            } else {
                schedules.computeIfAbsent(date, ignored -> new ArrayList<>())
                        .add(new CalendarScheduleResponse(
                                item.id(),
                                item.category(),
                                item.title(),
                                item.amountKrw(),
                                item.estimated()
                        ));
            }
        }

        return new CalendarMonthResponse(events, schedules, transactions);
    }

    private void addCashFlowItems(
            List<CalendarItem> items,
            Long userId,
            YearMonth targetMonth,
            LocalDate from,
            LocalDate to
    ) {
        for (CashFlowEvent event : cashFlowEventRepository.findCalendarEvents(userId, from, to)) {
            // 정기수입 투영 규칙은 집계와 공유한다(MonthlyCashFlowProjection) — 캘린더와 리포트·홈이
            // 같은 달에 같은 이벤트를 보게 하기 위함.
            LocalDate date = MonthlyCashFlowProjection.resolveDate(event, targetMonth);
            if (date == null) {
                continue;
            }

            CalendarEventCategory category = calendarEventMapper.toCategory(event.getEventType());
            boolean transactional = category == CalendarEventCategory.TRANSACTION
                    || category == CalendarEventCategory.INVESTMENT;
            items.add(new CalendarItem(
                    "cashflow-" + event.getEventId() + "-" + date,
                    date,
                    category,
                    defaultTitle(event.getTitle(), category.shortLabel()),
                    calendarEventMapper.toSignedAmount(event.getAmount(), event.getFlowType()),
                    false,
                    transactional
            ));
        }
    }

    private void addDebtMaturityItems(
            List<CalendarItem> items,
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        for (Debt debt : debtRepository
                .findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(userId, from, to)) {
            String title = isBlank(debt.getInstitutionName())
                    ? "대출 만기"
                    : debt.getInstitutionName() + " 대출 만기";
            items.add(new CalendarItem(
                    "debt-" + debt.getId(),
                    debt.getMaturityDate(),
                    CalendarEventCategory.MATURITY,
                    title,
                    null,
                    false,
                    false
            ));
        }
    }

    /**
     * 분배금(실지급 + 예상 투영)은 {@link DividendScheduleService} 단일 출처에서 받아 날짜에 찍는다.
     * 그 서비스의 {@code monthlyGross}(리포트·홈이 쓰는 값)와 같은 목록이라, 캘린더 표시 합과 항상 일치한다.
     */
    private void addDividendItems(
            List<CalendarItem> items,
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        for (DividendOccurrence occurrence : dividendScheduleService.occurrences(userId, from, to)) {
            String productName = isBlank(occurrence.productName()) ? "ETF" : occurrence.productName();
            String idPrefix = occurrence.estimated() ? "etf-dividend-" : "etf-dividend-actual-";
            String titleSuffix = occurrence.estimated() ? " 예상 분배금" : " 분배금";
            items.add(new CalendarItem(
                    idPrefix + occurrence.productId() + "-" + occurrence.date(),
                    occurrence.date(),
                    CalendarEventCategory.DIVIDEND,
                    productName + titleSuffix,
                    occurrence.amount(),
                    occurrence.estimated(),
                    false
            ));
        }
    }

    private YearMonth toYearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private String defaultTitle(String title, String fallback) {
        return isBlank(title) ? fallback : title;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CalendarItem(
            String id,
            LocalDate date,
            CalendarEventCategory category,
            String title,
            BigDecimal amountKrw,
            boolean estimated,
            boolean transactional
    ) {
    }
}
