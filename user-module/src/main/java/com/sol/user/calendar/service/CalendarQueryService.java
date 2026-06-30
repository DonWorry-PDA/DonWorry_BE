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
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // 실지급(actual)이 차지한 (상품, 월)을 모아, 투영(expected)이 같은 달을 덮어쓰지 않게 한다.
        Set<String> actualDividendMonths = new HashSet<>();
        addActualDividendItems(items, userId, from, to, actualDividendMonths);
        addExpectedDividendItems(items, userId, from, to, actualDividendMonths);
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
            LocalDate date = resolveCashFlowDate(event, targetMonth);
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

    /**
     * 과거·현재 달의 실제 지급된 분배금(확정). dividend_history의 실지급 행을 payment_date에 그대로 표시한다.
     * 실지급이 차지한 (productId, 월)을 {@code actualMonths}에 모아, 투영이 같은 달을 중복 표시하지 않게 한다.
     */
    private void addActualDividendItems(
            List<CalendarItem> items,
            Long userId,
            LocalDate from,
            LocalDate to,
            Set<String> actualMonths
    ) {
        for (HoldingDividendPaymentProjection input
                : holdingRepository.findDividendPaymentsByUserId(userId, from, to)) {
            if (input.getProductId() == null || input.getQuantity() == null
                    || input.getAmountPerUnit() == null || input.getPaymentDate() == null) {
                continue;
            }
            BigDecimal amount = input.getAmountPerUnit()
                    .multiply(input.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);
            String productName = isBlank(input.getProductName()) ? "ETF" : input.getProductName();
            items.add(new CalendarItem(
                    "etf-dividend-actual-" + input.getProductId() + "-" + input.getPaymentDate(),
                    input.getPaymentDate(),
                    CalendarEventCategory.DIVIDEND,
                    productName + " 분배금",
                    amount,
                    false,
                    false
            ));
            actualMonths.add(dividendMonthKey(input.getProductId(), input.getPaymentDate()));
        }
    }

    /**
     * 예상 분배금 투영(#307). 예전엔 "최신지급일+interval"부터 미래로만 깔려, 실지급 기록이 드문드문한
     * 과거 달(예: 분기배당의 비지급 달)이 캘린더에서 비었다. 이제 최신지급일을 앵커로 interval 격자를
     * 조회 구간 전체(과거·미래)에 깔고, 실지급(actual)이 있는 (productId, 월)만 건너뛴다.
     */
    private void addExpectedDividendItems(
            List<CalendarItem> items,
            Long userId,
            LocalDate from,
            LocalDate to,
            Set<String> actualMonths
    ) {
        for (HoldingDividendCalendarProjection input
                : holdingRepository.findDividendCalendarInputsByUserId(userId)) {
            if (!canProjectDividend(input)) {
                continue;
            }

            long interval = input.getDistributionIntervalMonths();
            BigDecimal expectedAmount = input.getAmountPerUnit()
                    .multiply(input.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);

            // 앵커(최신지급일)에서 interval 격자로 [from, to]를 덮는 첫 지점으로 이동.
            LocalDate projectedDate = input.getLatestPaymentDate();
            while (projectedDate.isAfter(from)) {
                projectedDate = projectedDate.minusMonths(interval);
            }
            while (projectedDate.isBefore(from)) {
                projectedDate = projectedDate.plusMonths(interval);
            }

            while (!projectedDate.isAfter(to)) {
                if (!actualMonths.contains(dividendMonthKey(input.getProductId(), projectedDate))) {
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
                }
                projectedDate = projectedDate.plusMonths(interval);
            }
        }
    }

    /** 분배금 중복 방지 키 — (productId, 해당 월). 실지급과 같은 달의 투영을 막는다. */
    private String dividendMonthKey(Long productId, LocalDate date) {
        return productId + "-" + YearMonth.from(date);
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
