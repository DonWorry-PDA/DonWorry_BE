package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.mapper.MonthlyReportMapper;
import com.sol.user.report.repository.MonthlyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportService {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";

    private final AssetAggregator assetAggregator;
    private final MonthlyReportRepository reportRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final PensionRepository pensionRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final ProductBatchClient productBatchClient;
    private final MonthlyReportSummaryGenerator summaryGenerator;
    private final MonthlyReportMapper mapper;

    public MonthlyReportResponse getMonthlyReport(Long userId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        YearMonth prevYm = ym.minusMonths(1);
        LocalDate prevStart = prevYm.atDay(1);
        LocalDate prevEnd = prevYm.atEndOfMonth();

        // 자산 변화
        BigDecimal currentTotal = assetAggregator.aggregate(userId).grossTotal();
        Optional<MonthlyReport> prevSnapshot =
                reportRepository.findByUserUserIdAndCurrentMonth(userId, prevYm.toString());
        BigDecimal previousTotal = prevSnapshot.map(MonthlyReport::getTotalAsset).orElse(null);
        BigDecimal changeAmount = previousTotal != null ? currentTotal.subtract(previousTotal) : null;

        // 연금·배당·이자 (이번 달 실제 수령액)
        BigDecimal receivedPension = cashFlowEventRepository
                .sumAmountByEventTypeInPeriod(userId, "PENSION", start, end);
        BigDecimal dividendAmount = cashFlowEventRepository
                .sumAmountByEventTypeInPeriod(userId, "DIVIDEND", start, end);
        BigDecimal prevDividend = cashFlowEventRepository
                .sumAmountByEventTypeInPeriod(userId, "DIVIDEND", prevStart, prevEnd);
        BigDecimal dividendChangeRate = calcChangeRate(dividendAmount, prevDividend);
        BigDecimal interestAmount = cashFlowEventRepository
                .sumAmountByEventTypeInPeriod(userId, "INTEREST", start, end);

        // 소비
        BigDecimal expenseAmount = cashFlowEventRepository
                .sumAmountByFlowTypeInPeriod(userId, "EXPENSE", start, end);
        BigDecimal incomeAmount = cashFlowEventRepository
                .sumAmountByFlowTypeInPeriod(userId, "INCOME", start, end);
        int spendingRatio = calcSpendingRatio(expenseAmount, incomeAmount);
        String judgment = calcJudgment(spendingRatio);

        // 다음 달 미리보기
        BigDecimal nextPension = pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO);
        BigDecimal nextDividend = calcMonthlyEtfDividend(userId);
        BigDecimal incomingTotal = nextPension.add(nextDividend);
        BigDecimal outgoingTotal = cashFlowEventRepository
                .sumRecurringExpenseInPeriod(userId, start, end);
        BigDecimal currentBalance = accountRepository.findByUserUserId(userId).stream()
                .map(Account::getDepositBalance)
                .map(b -> b == null ? BigDecimal.ZERO : b)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanceSufficient = currentBalance.compareTo(outgoingTotal) >= 0;

        List<String> summary = summaryGenerator.generate(
                dividendAmount, dividendChangeRate, spendingRatio, balanceSufficient);

        return mapper.toResponse(
                ym, summary,
                mapper.toAssetChange(changeAmount, previousTotal, currentTotal),
                mapper.toIncome(receivedPension, dividendAmount, dividendChangeRate, interestAmount),
                mapper.toSpending(expenseAmount, incomeAmount, judgment, spendingRatio),
                mapper.toNextMonthPreview(incomingTotal, nextPension, nextDividend, outgoingTotal, balanceSufficient)
        );
    }

    private BigDecimal calcChangeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private int calcSpendingRatio(BigDecimal expense, BigDecimal income) {
        if (income == null || income.signum() <= 0) {
            return expense != null && expense.signum() > 0 ? 999 : 0;
        }
        return expense.multiply(BigDecimal.valueOf(100))
                .divide(income, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String calcJudgment(int ratio) {
        if (ratio <= 80) return "적정";
        if (ratio <= 100) return "주의";
        return "과다";
    }

    private BigDecimal calcMonthlyEtfDividend(Long userId) {
        List<EtfHolding> holdings = holdingRepository.findAllHoldingsByUserId(userId);
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> productIds = holdings.stream().map(EtfHolding::getProductId).toList();
        Map<Long, BigDecimal> monthlyDividendMap = productBatchClient.fetchEtfMonthlyDividends(productIds);
        return holdings.stream()
                .map(h -> monthlyDividendMap.getOrDefault(h.getProductId(), BigDecimal.ZERO)
                        .multiply(h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }
}
