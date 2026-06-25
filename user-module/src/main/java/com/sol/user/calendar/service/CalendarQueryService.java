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
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final HoldingRepository holdingRepository;
    private final CalendarEventMapper calendarEventMapper;

    public CalendarMonthResponse getMonth(Long userId, int year, int month) {
        YearMonth targetMonth = toYearMonth(year, month);
        LocalDate from = targetMonth.atDay(1);
        LocalDate to = targetMonth.atEndOfMonth();

        List<CalendarItem> items = new ArrayList<>();
        addCashFlowItems(items, userId, targetMonth, from, to);
        addDebtMaturityItems(items, userId, from, to);
        addExpectedDividendItems(items, userId, from, to);
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
            LocalDate date = resolveCashFlowDate(event, targetMonth);
            if (date == null) {
                continue;
            }

            CalendarEventCategory category = calendarEventMapper.toCategory(event.getEventType());
            boolean transactional = category == CalendarEventCategory.TRANSACTION;
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

    private LocalDate resolveCashFlowDate(CashFlowEvent event, YearMonth targetMonth) {
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

    private void addExpectedDividendItems(
            List<CalendarItem> items,
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        for (HoldingDividendCalendarProjection input
                : holdingRepository.findDividendCalendarInputsByUserId(userId)) {
            if (!canProjectDividend(input)) {
                continue;
            }

            long interval = input.getDistributionIntervalMonths();
            LocalDate projectedDate = input.getLatestPaymentDate().plusMonths(interval);
            while (projectedDate.isBefore(from)) {
                projectedDate = projectedDate.plusMonths(interval);
            }

            BigDecimal expectedAmount = input.getAmountPerUnit()
                    .multiply(input.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);
            while (!projectedDate.isAfter(to)) {
                String productName = isBlank(input.getProductName()) ? "ETF" : input.getProductName();
                items.add(new CalendarItem(
                        "etf-dividend-" + input.getProductId() + "-" + projectedDate,
                        projectedDate,
                        CalendarEventCategory.DIVIDEND,
                        productName + " 예상 분배금",
                        expectedAmount,
                        true,
                        false
                ));
                projectedDate = projectedDate.plusMonths(interval);
            }
        }
    }

    private boolean canProjectDividend(HoldingDividendCalendarProjection input) {
        return input.getProductId() != null
                && input.getQuantity() != null
                && input.getAmountPerUnit() != null
                && input.getLatestPaymentDate() != null
                && input.getDistributionIntervalMonths() != null
                && input.getDistributionIntervalMonths() > 0;
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
