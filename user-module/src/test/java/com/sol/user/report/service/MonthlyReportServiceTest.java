package com.sol.user.report.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetBreakdown;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonthlyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth MAY = YearMonth.of(2026, 5);

    @Mock AssetAggregator assetAggregator;
    @Mock MonthlyReportRepository reportRepository;
    @Mock CashFlowEventRepository cashFlowEventRepository;
    @Mock PensionRepository pensionRepository;
    @Mock AccountRepository accountRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock ProductBatchClient productBatchClient;
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
        assertThat(response.assetChange().currentTotal()).isEqualByComparingTo("250_000_000".replace("_", ""));
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
    void 연금_배당_이자를_각각_별도_집계한다() {
        stubDefaults();
        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), eq("PENSION"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(1_200_000));
        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), eq("DIVIDEND"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(100_000), won(88_000)); // 당월, 전월
        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), eq("INTEREST"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(32_450));

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
    void 전월배당이_0이면_변화율이_null() {
        stubDefaults();
        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), eq("DIVIDEND"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(100_000), BigDecimal.ZERO);

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.income().dividendChangeRate()).isNull();
    }

    @Test
    void 배당_변화율을_소수점1자리로_계산한다() {
        stubDefaults();
        // 100_000 / 88_000 - 1 = 13.6...% → 13.6
        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), eq("DIVIDEND"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(100_000), won(88_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.income().dividendChangeRate()).isEqualByComparingTo("13.6");
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

    // ─── 다음 달 미리보기 ──────────────────────────────────────────

    @Test
    void 들어올돈은_연금과_배당의_합() {
        stubDefaults();
        when(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .thenReturn(Optional.of(won(1_200_000)));

        EtfHolding holding = etfHolding(1L, new BigDecimal("10"));
        when(holdingRepository.findAllHoldingsByUserId(USER_ID)).thenReturn(List.of(holding));
        when(productBatchClient.fetchEtfMonthlyDividends(anyList()))
                .thenReturn(Map.of(1L, new BigDecimal("10000")));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().pensionAmount()).isEqualByComparingTo("1200000");
        assertThat(response.nextMonthPreview().dividendAmount()).isEqualByComparingTo("100000");
        assertThat(response.nextMonthPreview().incomingTotal()).isEqualByComparingTo("1300000");
    }

    @Test
    void 잔액이_나갈돈보다_크면_잔액충분() {
        stubDefaults();
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(3_000_000))));
        when(cashFlowEventRepository.sumRecurringExpenseInPeriod(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(2_150_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().balanceSufficient()).isTrue();
    }

    @Test
    void 잔액이_나갈돈보다_작으면_잔액부족() {
        stubDefaults();
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(1_000_000))));
        when(cashFlowEventRepository.sumRecurringExpenseInPeriod(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(won(2_150_000));

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().balanceSufficient()).isFalse();
    }

    @Test
    void 보유종목_없으면_다음달_배당_0() {
        stubDefaults();
        when(holdingRepository.findAllHoldingsByUserId(USER_ID)).thenReturn(List.of());

        MonthlyReportResponse response = service.getMonthlyReport(USER_ID, JUNE);

        assertThat(response.nextMonthPreview().dividendAmount()).isEqualByComparingTo("0");
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private void stubDefaults() {
        AssetBreakdown breakdown = new AssetBreakdown(
                won(250_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(assetAggregator.aggregate(USER_ID)).thenReturn(breakdown);

        when(reportRepository.findByUserUserIdAndCurrentMonth(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        when(cashFlowEventRepository.sumAmountByEventTypeInPeriod(
                eq(USER_ID), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cashFlowEventRepository.sumAmountByFlowTypeInPeriod(
                eq(USER_ID), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cashFlowEventRepository.sumRecurringExpenseInPeriod(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);

        when(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .thenReturn(Optional.empty());
        when(holdingRepository.findAllHoldingsByUserId(USER_ID)).thenReturn(List.of());
        when(accountRepository.findByUserUserId(USER_ID))
                .thenReturn(List.of(accountWithBalance(won(5_000_000))));
        when(summaryGenerator.generate(any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of("line1", "line2", "line3"));
    }

    private void stubSpending(BigDecimal expense, BigDecimal income) {
        when(cashFlowEventRepository.sumAmountByFlowTypeInPeriod(
                eq(USER_ID), eq("EXPENSE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(expense);
        when(cashFlowEventRepository.sumAmountByFlowTypeInPeriod(
                eq(USER_ID), eq("INCOME"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(income);
    }

    private MonthlyReport snapshotOf(BigDecimal totalAsset) {
        User user = mock(User.class);
        MonthlyReport report = MonthlyReport.snapshot(user, MAY.toString(), totalAsset);
        return report;
    }

    private Account accountWithBalance(BigDecimal balance) {
        User user = mock(User.class);
        Account account = new Account(user, "DON_WORRY", "신한은행", "1234", balance, false);
        return account;
    }

    private EtfHolding etfHolding(Long productId, BigDecimal quantity) {
        return new EtfHolding() {
            @Override public Long getHoldingId() { return productId; }
            @Override public Long getProductId() { return productId; }
            @Override public BigDecimal getQuantity() { return quantity; }
        };
    }

    private BigDecimal won(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
