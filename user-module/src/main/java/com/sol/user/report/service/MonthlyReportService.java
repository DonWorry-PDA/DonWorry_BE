package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.pension.repository.PensionRepository;
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
    private final DepositDetailClient depositDetailClient;
    private final EtfDividendCalculator etfDividendCalculator;
    private final MonthlyReportSummaryGenerator summaryGenerator;
    private final MonthlyReportMapper mapper;

    public MonthlyReportResponse getMonthlyReport(Long userId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        YearMonth prevYm = ym.minusMonths(1);

        // 자산 변화. 끝값(currentTotal)은 '보는 달'의 스냅샷을 쓴다 — 과거 달을 봐도 끝값이 항상
        // live 총자산(지금)이 되던 버그 방지. 현재월처럼 스냅샷이 아직 없으면 live 합계로 폴백한다.
        BigDecimal currentTotal = reportRepository.findByUserUserIdAndCurrentMonth(userId, ym.toString())
                .map(MonthlyReport::getTotalAsset)
                .filter(t -> t != null)
                .orElseGet(() -> assetAggregator.aggregate(userId).grossTotal());
        Optional<MonthlyReport> prevSnapshot =
                reportRepository.findByUserUserIdAndCurrentMonth(userId, prevYm.toString());
        BigDecimal previousTotal = prevSnapshot.map(MonthlyReport::getTotalAsset).orElse(null);
        BigDecimal changeAmount = previousTotal != null ? currentTotal.subtract(previousTotal) : null;

        // 연금·배당·이자 (이번 달 실제 수령액)
        BigDecimal receivedPension = cashFlowEventRepository
                .sumAmountByEventTypeInPeriod(userId, "PENSION", start, end);
        // 배당은 시드 이벤트가 아니라 실제 분배 이벤트 기준으로 계산한다(#216, #301).
        // 캘린더 화면과 동일한 그리드 알고리즘을 사용해 당월에 분배가 있는 ETF만 합산한다.
        // 전월 대비 증감률은 별도 산출하지 않는다(null).
        BigDecimal dividendAmount = calcMonthlyEtfDividend(userId, ym);
        BigDecimal dividendChangeRate = null;
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
        YearMonth nextYm = ym.plusMonths(1);
        BigDecimal nextPension = pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO);
        BigDecimal nextDividend = calcMonthlyEtfDividend(userId, nextYm);
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        // 다음 달 예금 이자 = 잔고×금리로 추정(recurring). 이전엔 '이번 달 실제 이자'를 복사해, 현재 부분월처럼
        // 아직 이자 이벤트가 0인 달엔 다음 달 이자도 0으로 빠지던 버그(#309). 자산분석 income과 동일 공식.
        BigDecimal nextInterest = estimateMonthlyDepositInterest(accounts);
        BigDecimal incomingTotal = nextPension.add(nextDividend).add(nextInterest);
        // 나갈 돈 = recurring 고정지출(관리비·보험·대출) 합계. 날짜창에 묶지 않아 어느 달을 봐도 0이 되지 않는다.
        // 시드 단계에서 관리비엔 이미 그 달 cashFactor가 반영돼 있고 보험·대출은 고정이므로(buildMonthEvents),
        // 여기서 다시 계수를 곱하면 관리비 이중 변동·고정항목 흔들림이 생긴다 → 저장값을 그대로 합산한다.
        BigDecimal outgoingTotal = cashFlowEventRepository.sumRecurringExpense(userId)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal currentBalance = accounts.stream()
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
                mapper.toNextMonthPreview(incomingTotal, nextPension, nextDividend,
                        nextInterest, outgoingTotal, balanceSufficient)
        );
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

    /**
     * 특정 달(ym)에 실제로 분배 이벤트가 있는 ETF만 합산한 세전 배당(원, 반올림).
     * 이전의 스무딩(런레이트 × MonthlyVariation 계수) 방식 대신, 캘린더 화면과 동일한
     * 실제 분배 그리드 기반 계산을 사용한다.
     */
    private BigDecimal calcMonthlyEtfDividend(Long userId, YearMonth ym) {
        return etfDividendCalculator.actualMonthlyDividend(userId, ym);
    }

    /**
     * 다음 달 예금 이자 추정 = Σ(예금 잔고 × 금리 / 1200). 예금 이자는 매달 들어오는 recurring이라,
     * '이번 달 실제 수령액'(현재 부분월엔 0일 수 있음)을 복사하지 않고 잔고 기준으로 산출한다(#309).
     * 자산분석 income({@code AssetIncomeService})의 예금 이자와 동일 공식이라 화면 간 정합한다.
     */
    private BigDecimal estimateMonthlyDepositInterest(List<Account> accounts) {
        List<Long> depositProductIds = accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .map(Account::getProductId)
                .toList();
        if (depositProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, DepositDetailItem> details = depositDetailClient.fetchDepositDetails(depositProductIds);
        return accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .filter(a -> details.containsKey(a.getProductId()))
                .map(a -> {
                    DepositDetailItem detail = details.get(a.getProductId());
                    BigDecimal balance = a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance();
                    BigDecimal rate = detail.interestRate() == null ? BigDecimal.ZERO : detail.interestRate();
                    return balance.multiply(rate).divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }
}
