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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetScheduleService {

    private final HoldingRepository holdingRepository;
    private final DebtRepository debtRepository;
    private final AccountRepository accountRepository;
    private final DepositDetailClient depositDetailClient;

    /**
     * Public API — uses real today.
     */
    public AssetScheduleResponse getSchedule(Long userId, int months) {
        return getSchedule(userId, months, LocalDate.now());
    }

    /**
     * Package-private overload — accepts a fixed today for deterministic testing.
     */
    AssetScheduleResponse getSchedule(Long userId, int months, LocalDate today) {
        months = Math.max(1, Math.min(12, months));
        LocalDate endExclusive = today.plusMonths(months);
        List<AssetScheduleEvent> events = new ArrayList<>();

        addEtfDividendEvents(events, userId, today, endExclusive);
        addDepositEvents(events, userId, today, endExclusive);
        addDebtMaturityEvents(events, userId, today, endExclusive);

        events.sort(Comparator.comparing(AssetScheduleEvent::getDate));

        return AssetScheduleResponse.builder()
                .events(events)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ETF_DIVIDEND
    // ─────────────────────────────────────────────────────────────────────────

    private void addEtfDividendEvents(
            List<AssetScheduleEvent> events,
            Long userId,
            LocalDate today,
            LocalDate endExclusive
    ) {
        for (HoldingDividendCalendarProjection proj
                : holdingRepository.findDividendCalendarInputsByUserId(userId)) {
            if (!canProjectDividend(proj)) {
                continue;
            }

            long interval = proj.getDistributionIntervalMonths();
            if (interval <= 0) continue;
            LocalDate projectedDate = proj.getLatestPaymentDate().plusMonths(interval);
            while (projectedDate.isBefore(today)) {
                projectedDate = projectedDate.plusMonths(interval);
            }

            BigDecimal amount = proj.getAmountPerUnit()
                    .multiply(proj.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);

            String productName = isBlank(proj.getProductName()) ? "ETF" : proj.getProductName();

            while (projectedDate.isBefore(endExclusive)) {
                events.add(AssetScheduleEvent.builder()
                        .date(projectedDate)
                        .type("ETF_DIVIDEND")
                        .label(productName + " 예상 분배금")
                        .amount(amount)
                        .estimated(true)
                        .build());
                projectedDate = projectedDate.plusMonths(interval);
            }
        }
    }

    private boolean canProjectDividend(HoldingDividendCalendarProjection proj) {
        return proj.getProductId() != null
                && proj.getQuantity() != null
                && proj.getAmountPerUnit() != null
                && proj.getLatestPaymentDate() != null
                && proj.getDistributionIntervalMonths() != null
                && proj.getDistributionIntervalMonths() > 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPOSIT_INTEREST + DEPOSIT_MATURITY
    // ─────────────────────────────────────────────────────────────────────────

    private void addDepositEvents(
            List<AssetScheduleEvent> events,
            Long userId,
            LocalDate today,
            LocalDate endExclusive
    ) {
        List<Account> accounts = accountRepository.findByUserUserId(userId);

        List<Account> depositAccounts = accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()))
                .filter(a -> a.getProductId() != null)
                .filter(a -> a.getOpenedAt() != null)
                .toList();

        if (depositAccounts.isEmpty()) {
            return;
        }

        List<Long> productIds = depositAccounts.stream()
                .map(Account::getProductId)
                .collect(Collectors.toList());

        Map<Long, DepositDetailItem> details = depositDetailClient.fetchDepositDetails(productIds);

        for (Account account : depositAccounts) {
            DepositDetailItem detail = details.get(account.getProductId());
            if (detail == null) {
                continue;
            }

            addDepositInterestEvents(events, account, detail, today, endExclusive);
            addDepositMaturityEvent(events, account, detail, today, endExclusive);
        }
    }

    private void addDepositInterestEvents(
            List<AssetScheduleEvent> events,
            Account account,
            DepositDetailItem detail,
            LocalDate today,
            LocalDate endExclusive
    ) {
        if (detail.interestRate() == null) {
            return;
        }

        BigDecimal balance = account.getDepositBalance() == null
                ? BigDecimal.ZERO : account.getDepositBalance();
        BigDecimal monthlyAmount = balance
                .multiply(detail.interestRate())
                .divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP);

        int paymentDay = account.getOpenedAt().getDayOfMonth();
        String label = isBlank(account.getInstitutionName())
                ? "예금 이자"
                : account.getInstitutionName() + " 예금 이자";

        LocalDate cursor = today;
        while (cursor.isBefore(endExclusive)) {
            YearMonth ym = YearMonth.from(cursor);
            int cappedDay = Math.min(paymentDay, ym.lengthOfMonth());
            LocalDate payDate = ym.atDay(cappedDay);

            if (!payDate.isBefore(today) && payDate.isBefore(endExclusive)) {
                events.add(AssetScheduleEvent.builder()
                        .date(payDate)
                        .type("DEPOSIT_INTEREST")
                        .label(label)
                        .amount(monthlyAmount)
                        .estimated(false)
                        .build());
            }

            cursor = cursor.plusMonths(1).withDayOfMonth(1);
        }
    }

    private void addDepositMaturityEvent(
            List<AssetScheduleEvent> events,
            Account account,
            DepositDetailItem detail,
            LocalDate today,
            LocalDate endExclusive
    ) {
        if (detail.maturityMonths() == null) {
            return;
        }

        LocalDate maturityDate = account.getOpenedAt().plusMonths(detail.maturityMonths());

        if (!maturityDate.isBefore(today) && maturityDate.isBefore(endExclusive)) {
            String label = isBlank(account.getInstitutionName())
                    ? "정기예금 만기"
                    : account.getInstitutionName() + " 정기예금 만기";

            events.add(AssetScheduleEvent.builder()
                    .date(maturityDate)
                    .type("DEPOSIT_MATURITY")
                    .label(label)
                    .amount(account.getDepositBalance() == null ? BigDecimal.ZERO : account.getDepositBalance())
                    .estimated(false)
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEBT_MATURITY
    // ─────────────────────────────────────────────────────────────────────────

    private void addDebtMaturityEvents(
            List<AssetScheduleEvent> events,
            Long userId,
            LocalDate today,
            LocalDate endExclusive
    ) {
        LocalDate to = endExclusive.minusDays(1);
        List<Debt> debts = debtRepository
                .findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(userId, today, to);

        for (Debt debt : debts) {
            String label = isBlank(debt.getInstitutionName())
                    ? "대출 만기"
                    : debt.getInstitutionName() + " 대출 만기";

            events.add(AssetScheduleEvent.builder()
                    .date(debt.getMaturityDate())
                    .type("DEBT_MATURITY")
                    .label(label)
                    .amount(null)
                    .estimated(false)
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
