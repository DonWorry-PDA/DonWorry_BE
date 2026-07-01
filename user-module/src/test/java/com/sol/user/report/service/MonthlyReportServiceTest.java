package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedEvent;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedMonth;
import com.sol.user.holding.service.DividendScheduleService;
import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.mapper.MonthlyReportMapper;
import com.sol.user.report.repository.MonthlyReportRepository;
import com.sol.user.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonthlyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Mock AssetAggregator assetAggregator;
    @Mock MonthlyReportRepository reportRepository;
    @Mock CashFlowEventRepository cashFlowEventRepository;
    @Mock AccountRepository accountRepository;
    @Mock MonthlyCashFlowProjection projection;
    @Mock DividendScheduleService dividendScheduleService;
    @Mock MonthlyReportSummaryGenerator summaryGenerator;
    @Spy MonthlyReportMapper mapper;

    @InjectMocks MonthlyReportService service;

    // ─── 자산 변화 ────────────────────────────────────────────────

    @Test
    void 이전달_스냅샷_없으면_changeAmount와_previousTotal이_null() {
        stubDefaults();
        when(reportRepository.findByUserUserIdAndCurrentMonth(USER_ID, MAY.toString()))
                .thenReturn(Optional.empty());

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.assetChange().changeAmount()).isNull();
        assertThat(response.assetChange().previousTotal()).isNull();
        assertThat(response.assetChange().currentTotal()).isEqualByComparingTo("250000000");
    }

    @Test
    void 이전달_스냅샷_있으면_변화액을_계산한다() {
        stubDefaults();
        MonthlyReport prevReport = snapshotOf(won(248_000_000));
        when(reportRepository.findByUserUserIdAndCurrentMonth(USER_ID, MAY.toString()))
                .thenReturn(Optional.of(prevReport));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.assetChange().previousTotal()).isEqualByComparingTo("248000000");
        assertThat(response.assetChange().changeAmount()).isEqualByComparingTo("2000000");
    }

    // ─── 연금·배당·이자 ──────────────────────────────────────────────

    @Test
    void 연금_이자는_투영으로_배당은_스케줄로_집계한다() {
        stubDefaults();
        // 정기수입은 recurring을 조회월로 투영해 집계한다(시드월 다음 달에도 사라지지 않음).
        when(projection.project(USER_ID, JUNE))
                .thenReturn(pm(pension(1_200_000), interest(32_450)));
        // 분배금은 캘린더와 같은 스케줄 소스 — 월별 계수 없이 그 달 스케줄 값 그대로.
        when(dividendScheduleService.monthlyGross(USER_ID, JUNE)).thenReturn(won(100_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.income().pensionAmount()).isEqualByComparingTo("1200000");
        assertThat(response.income().dividendAmount()).isEqualByComparingTo("100000");
        assertThat(response.income().interestAmount()).isEqualByComparingTo("32450");
    }

    @Test
    void 연금수령이_없으면_pensionAmount가_0() {
        stubDefaults();

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.income().pensionAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 배당_변화율은_보유ETF_기반이라_표시하지_않는다() {
        stubDefaults();

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.income().dividendChangeRate()).isNull();
    }

    // ─── 소비 ─────────────────────────────────────────────────────

    @Test
    void 소비비율_80이하면_적정() {
        stubDefaults();
        stubSpending(won(1_600_000), won(2_000_000)); // 80%

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().judgment()).isEqualTo("적정");
        assertThat(response.spending().spendingRatio()).isEqualTo(80);
    }

    @Test
    void 소비비율_81이상100이하면_주의() {
        stubDefaults();
        stubSpending(won(1_900_000), won(2_000_000)); // 95%

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().judgment()).isEqualTo("주의");
        assertThat(response.spending().spendingRatio()).isEqualTo(95);
    }

    @Test
    void 소비비율_100초과면_과다() {
        stubDefaults();
        stubSpending(won(2_500_000), won(2_000_000)); // 125%

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().judgment()).isEqualTo("과다");
        assertThat(response.spending().spendingRatio()).isEqualTo(125);
    }

    @Test
    void 수입이_0이고_지출도_0이면_비율_0() {
        stubDefaults();
        stubSpending(BigDecimal.ZERO, BigDecimal.ZERO);

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().spendingRatio()).isEqualTo(0);
    }

    @Test
    void 수입이_0이고_지출있으면_비율_999() {
        stubDefaults();
        stubSpending(won(1_000_000), BigDecimal.ZERO);

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().spendingRatio()).isEqualTo(999);
        assertThat(response.spending().judgment()).isEqualTo("과다");
    }

    @Test
    void 소비비율_수입에_분배금도_포함된다() {
        stubDefaults();
        // 수입(캐시플로우) 1,000,000 + 분배금 1,000,000 = 2,000,000, 지출 1,000,000 → 50%
        stubSpending(won(1_000_000), won(1_000_000));
        when(dividendScheduleService.monthlyGross(USER_ID, JUNE)).thenReturn(won(1_000_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.spending().spendingRatio()).isEqualTo(50);
    }

    // ─── 다음 달 미리보기 ──────────────────────────────────────────

    @Test
    void 들어올돈은_연금과_배당과_이자의_합() {
        stubDefaults();
        // 다음 달(7월)도 이번 달과 같은 투영·스케줄 소스로 산출한다.
        when(projection.project(USER_ID, JULY)).thenReturn(pm(pension(1_200_000)));
        when(dividendScheduleService.monthlyGross(USER_ID, JULY)).thenReturn(won(100_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().pensionAmount()).isEqualByComparingTo("1200000");
        assertThat(response.nextMonthPreview().dividendAmount()).isEqualByComparingTo("100000");
        assertThat(response.nextMonthPreview().interestAmount()).isEqualByComparingTo("0");
        assertThat(response.nextMonthPreview().incomingTotal()).isEqualByComparingTo("1300000");
    }

    @Test
    void 잔액이_나갈돈보다_크면_잔액충분() {
        stubDefaults();
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(3_000_000))));
        when(cashFlowEventRepository.sumRecurringExpense(eq(USER_ID)))
                .thenReturn(won(2_150_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().balanceSufficient()).isTrue();
    }

    @Test
    void 잔액이_나갈돈보다_작으면_잔액부족() {
        stubDefaults();
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(1_000_000))));
        when(cashFlowEventRepository.sumRecurringExpense(eq(USER_ID)))
                .thenReturn(won(2_150_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().balanceSufficient()).isFalse();
    }

    @Test
    void 보유종목_없으면_다음달_배당_0() {
        stubDefaults(); // 기본 분배금 ZERO

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().dividendAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 다음달_들어올돈에_이자가_투영으로_포함된다() {
        // 정기 이자는 recurring 투영으로 다음 달에도 잡힌다(시드월 다음 달에도 0이 아님).
        stubDefaults();
        when(projection.project(USER_ID, JULY)).thenReturn(pm(interest(100_000)));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().interestAmount()).isEqualByComparingTo("100000");
        // 연금 0 + 배당 0 + 이자 100,000
        assertThat(response.nextMonthPreview().incomingTotal()).isEqualByComparingTo("100000");
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private void stubDefaults() {
        AssetBreakdown breakdown = new AssetBreakdown(
                won(250_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(assetAggregator.aggregate(USER_ID)).thenReturn(breakdown);

        when(reportRepository.findByUserUserIdAndCurrentMonth(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        when(projection.project(eq(USER_ID), any())).thenReturn(pm());
        when(dividendScheduleService.monthlyGross(eq(USER_ID), any())).thenReturn(BigDecimal.ZERO);
        when(cashFlowEventRepository.sumRecurringExpense(eq(USER_ID)))
                .thenReturn(BigDecimal.ZERO);
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(5_000_000))));
        when(summaryGenerator.generate(any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of("line1", "line2", "line3"));
    }

    /** 이번 달(JUNE) 소비/수입 투영 — 수입은 일반 INCOME 이벤트로, 지출은 EXPENSE 이벤트로 준다. */
    private void stubSpending(BigDecimal expense, BigDecimal income) {
        when(projection.project(USER_ID, JUNE)).thenReturn(pm(
                new ProjectedEvent("SALARY", "INCOME", income),
                new ProjectedEvent("MAINTENANCE", "EXPENSE", expense)));
    }

    private ProjectedMonth pm(ProjectedEvent... events) {
        return new ProjectedMonth(List.of(events));
    }

    private ProjectedEvent pension(long amount) {
        return new ProjectedEvent("PENSION", "INCOME", won(amount));
    }

    private ProjectedEvent interest(long amount) {
        return new ProjectedEvent("INTEREST", "INCOME", won(amount));
    }

    private MonthlyReport snapshotOf(BigDecimal totalAsset) {
        User user = mock(User.class);
        return MonthlyReport.snapshot(user, MAY.toString(), totalAsset);
    }

    private Account accountWithBalance(BigDecimal balance) {
        User user = mock(User.class);
        return new Account(user, "DON_WORRY", "신한은행", "1234", balance, false);
    }

    private BigDecimal won(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
