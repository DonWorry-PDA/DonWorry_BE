package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedMonth;
import com.sol.user.holding.service.DividendScheduleService;
import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.mapper.MonthlyReportMapper;
import com.sol.user.report.repository.MonthlyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportService {

    private final AssetAggregator assetAggregator;
    private final MonthlyReportRepository reportRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final AccountRepository accountRepository;
    private final MonthlyCashFlowProjection projection;
    private final DividendScheduleService dividendScheduleService;
    private final MonthlyReportSummaryGenerator summaryGenerator;
    private final MonthlyReportMapper mapper;

    public MonthlyReportResponse getMonthlyReport(Long userId, YearMonth ym) {
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

        // 연금·배당·이자 (이번 달 수입). 정기수입(연금·이자)은 recurring을 조회월로 투영해 집계한다 —
        // 시드월 다음 달부터 연금·이자가 0으로 사라지던 버그 수정. 캘린더와 같은 투영 규칙을 쓴다.
        ProjectedMonth thisMonth = projection.project(userId, ym);
        BigDecimal receivedPension = thisMonth.sumEventType("PENSION");
        BigDecimal interestAmount = thisMonth.sumEventType("INTEREST");
        // 분배금은 캘린더와 같은 스케줄 소스(실지급+예상 투영)에서 받는다 — 홈·리포트·캘린더 분배금 일치.
        // (과거의 런레이트×월계수 방식은 화면마다 값이 달라 폐기.)
        BigDecimal dividendAmount = dividendScheduleService.monthlyGross(userId, ym);
        BigDecimal dividendChangeRate = null;

        // 소비. 수입 총액 = 캐시플로우 수입(연금·이자 등) + 분배금 → income 섹션과 같은 기준으로 소비비율 산출.
        BigDecimal expenseAmount = thisMonth.sumFlow("EXPENSE");
        BigDecimal incomeAmount = thisMonth.sumFlow("INCOME").add(dividendAmount);
        int spendingRatio = calcSpendingRatio(expenseAmount, incomeAmount);
        String judgment = calcJudgment(spendingRatio);

        // 다음 달 미리보기 — 이번 달과 같은 투영·스케줄 소스로 산출해 대칭을 맞춘다.
        YearMonth nextYm = ym.plusMonths(1);
        ProjectedMonth nextMonth = projection.project(userId, nextYm);
        BigDecimal nextPension = nextMonth.sumEventType("PENSION");
        BigDecimal nextInterest = nextMonth.sumEventType("INTEREST");
        BigDecimal nextDividend = dividendScheduleService.monthlyGross(userId, nextYm);
        BigDecimal incomingTotal = nextPension.add(nextDividend).add(nextInterest);
        // 나갈 돈 = recurring 고정지출(관리비·보험·대출) 합계. 날짜창에 묶지 않아 어느 달을 봐도 0이 되지 않는다.
        // 시드 단계에서 관리비엔 이미 그 달 cashFactor가 반영돼 있고 보험·대출은 고정이므로(buildMonthEvents),
        // 여기서 다시 계수를 곱하면 관리비 이중 변동·고정항목 흔들림이 생긴다 → 저장값을 그대로 합산한다.
        BigDecimal outgoingTotal = cashFlowEventRepository.sumRecurringExpense(userId)
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
}
