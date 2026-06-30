package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.cashflow.MonthlyVariation;
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
        // 배당은 시드 이벤트가 아니라 보유 ETF 기반 단일 출처로 계산한다(#216).
        // 보유수량은 고정이지만 실제 분배는 달마다 들쭉날쭉하므로 월별 계수(MonthlyVariation)를 적용한다 —
        // 자산 변화 백필과 같은 계수라 정합한다. 전월 대비 증감률은 별도 산출하지 않는다(null).
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
        // 다음 달 예금 이자 추정 = 이번 달 이자(고정 잔고 기준). 들어올 돈에 이자가 빠져 있던 버그 수정.
        BigDecimal nextInterest = interestAmount;
        BigDecimal incomingTotal = nextPension.add(nextDividend).add(nextInterest);
        // 나갈 돈 = recurring 고정지출(관리비·보험·대출) × 다음 달 계수. 날짜창에 묶지 않아 어느 달을 봐도 0이 되지 않는다.
        BigDecimal outgoingTotal = cashFlowEventRepository.sumRecurringExpense(userId)
                .multiply(MonthlyVariation.cashFactor(nextYm))
                .setScale(0, RoundingMode.HALF_UP);
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
     * 보유 ETF 월 배당(런레이트)에 그 달의 배당 계수를 곱한다. 보유 기준이라 값 자체는 고정이지만,
     * 실제 분배는 달마다 들쭉날쭉하므로 결정적 월별 계수로 흔든다 — 자산 변화 백필과 같은 계수(정합).
     */
    private BigDecimal calcMonthlyEtfDividend(Long userId, YearMonth ym) {
        List<EtfHolding> holdings = holdingRepository.findAllHoldingsByUserId(userId);
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> productIds = holdings.stream().map(EtfHolding::getProductId).toList();
        Map<Long, BigDecimal> monthlyDividendMap = productBatchClient.fetchEtfMonthlyDividends(productIds);
        BigDecimal base = holdings.stream()
                .map(h -> monthlyDividendMap.getOrDefault(h.getProductId(), BigDecimal.ZERO)
                        .multiply(h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return base.multiply(MonthlyVariation.dividendFactor(ym)).setScale(0, RoundingMode.HALF_UP);
    }
}
