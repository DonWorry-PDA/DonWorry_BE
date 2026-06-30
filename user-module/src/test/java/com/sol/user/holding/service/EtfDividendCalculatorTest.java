package com.sol.user.holding.service;

import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.holding.service.EtfDividendCalculator.DividendBreakdown;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EtfDividendCalculatorTest {

    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final ProductBatchClient productBatchClient = mock(ProductBatchClient.class);

    private final EtfDividendCalculator calculator =
            new EtfDividendCalculator(holdingRepository, productBatchClient);

    @Test
    void 보유ETF의_월_분배금을_종목별_단가x수량으로_합산한다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(
                holding(1L, 101L, new BigDecimal("10"), "BROKERAGE"),
                holding(2L, 102L, new BigDecimal("5"), "BROKERAGE")
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of(
                101L, new BigDecimal("10000"),  // 10주 × 10,000 = 100,000
                102L, new BigDecimal("4000")    // 5주 × 4,000 = 20,000
        ));

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("120000");
    }

    @Test
    void 연금_비연금_계좌를_분해해_세전_분배금을_집계한다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(
                holding(1L, 101L, new BigDecimal("10"), "BROKERAGE"),   // 비연금 100,000
                holding(2L, 201L, new BigDecimal("20"), "IRP"),         // 연금 40,000
                holding(3L, 202L, new BigDecimal("5"), "PENSION_SAVING")// 연금 10,000
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of(
                101L, new BigDecimal("10000"),
                201L, new BigDecimal("2000"),
                202L, new BigDecimal("2000")
        ));

        DividendBreakdown breakdown = calculator.monthlyDividendBreakdown(1L, Set.of());

        assertThat(breakdown.nonPensionGross()).isEqualByComparingTo("100000");
        assertThat(breakdown.pensionGross()).isEqualByComparingTo("50000");
        assertThat(breakdown.totalGross()).isEqualByComparingTo("150000");
    }

    @Test
    void 제외_종목은_분배금에서_뺀다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(
                holding(1L, 101L, new BigDecimal("10"), "BROKERAGE"),
                holding(2L, 102L, new BigDecimal("5"), "BROKERAGE")
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of(
                101L, new BigDecimal("10000"),
                102L, new BigDecimal("4000")
        ));

        // holdingId=2 제외 → 102 분배금(20,000) 빠지고 100,000만 남음
        DividendBreakdown breakdown = calculator.monthlyDividendBreakdown(1L, Set.of(2L));

        assertThat(breakdown.totalGross()).isEqualByComparingTo("100000");
    }

    @Test
    void 보유종목이_없으면_0이고_상품조회를_호출하지_않는다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of());

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("0");
        verifyNoInteractions(productBatchClient);
    }

    @Test
    void 분배금_정보가_없는_종목은_0으로_처리한다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(
                holding(1L, 101L, new BigDecimal("10"), "BROKERAGE")
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of());

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("0");
    }

    @Test
    void Product_조회가_실패하면_0으로_격리한다() {
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(
                holding(1L, 101L, new BigDecimal("10"), "BROKERAGE")
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList()))
                .willThrow(new RuntimeException("product service down"));

        // 외부 장애가 생활안정도 재계산·자산 sync를 깨지 않도록 fail-open
        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("0");
        // 호출 자체는 실제로 일어났고(=실패 경로를 탔고) 그 예외를 격리한 것임을 확인
        verify(productBatchClient).fetchEtfMonthlyDividends(List.of(101L));
    }

    @Test
    void actualMonthlyDividend_실제지급이_있는_달은_그_금액을_반환한다() {
        YearMonth ym = YearMonth.of(2026, 3);  // March (quarterly ETF pays in March)
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Actual payment: productId=101, quantity=10, amountPerUnit=2700, paymentDate=2026-03-15
        HoldingDividendPaymentProjection actual = payment(101L, new BigDecimal("10"), new BigDecimal("2700"), LocalDate.of(2026, 3, 15));
        given(holdingRepository.findDividendPaymentsByUserId(1L, from, to)).willReturn(List.of(actual));
        given(holdingRepository.findDividendCalendarInputsByUserId(1L)).willReturn(List.of()); // no projections

        // 10 × 2700 = 27,000
        assertThat(calculator.actualMonthlyDividend(1L, ym)).isEqualByComparingTo("27000");
    }

    @Test
    void actualMonthlyDividend_실제지급이_없고_그리드가_해당월에_걸리면_예상금액을_반환한다() {
        YearMonth ym = YearMonth.of(2026, 6);  // June
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        given(holdingRepository.findDividendPaymentsByUserId(1L, from, to)).willReturn(List.of());

        // ETF with latestPaymentDate=2026-03-15, interval=3 → grid: Mar, Jun, Sep, Dec
        // Jun is on the grid → contributes 5 × 1000 = 5,000
        HoldingDividendCalendarProjection projection = calendarInput(
                201L, new BigDecimal("5"), new BigDecimal("1000"),
                LocalDate.of(2026, 3, 15), 3);
        given(holdingRepository.findDividendCalendarInputsByUserId(1L)).willReturn(List.of(projection));

        assertThat(calculator.actualMonthlyDividend(1L, ym)).isEqualByComparingTo("5000");
    }

    @Test
    void actualMonthlyDividend_그리드가_해당월에_안_걸리면_0을_반환한다() {
        YearMonth ym = YearMonth.of(2026, 4);  // April — quarterly ETF does NOT pay in April if grid is Mar/Jun/Sep/Dec
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        given(holdingRepository.findDividendPaymentsByUserId(1L, from, to)).willReturn(List.of());

        // ETF with latestPaymentDate=2026-03-15, interval=3 → grid: Mar, Jun, Sep, Dec → April is NOT on the grid
        HoldingDividendCalendarProjection projection = calendarInput(
                201L, new BigDecimal("5"), new BigDecimal("1000"),
                LocalDate.of(2026, 3, 15), 3);
        given(holdingRepository.findDividendCalendarInputsByUserId(1L)).willReturn(List.of(projection));

        assertThat(calculator.actualMonthlyDividend(1L, ym)).isEqualByComparingTo("0");
    }

    @Test
    void actualMonthlyDividend_실제지급이_있는_종목의_예상분배는_중복산입되지_않는다() {
        YearMonth ym = YearMonth.of(2026, 3);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // productId=101: actual payment exists for March
        HoldingDividendPaymentProjection actual = payment(
            101L, new BigDecimal("10"), new BigDecimal("2700"), LocalDate.of(2026, 3, 15));
        given(holdingRepository.findDividendPaymentsByUserId(1L, from, to))
            .willReturn(List.of(actual));

        // productId=101: projection grid also lands on March (interval=3, anchor=2026-03-15 → Mar is on grid)
        // This should NOT be added again — deduplication must block it
        HoldingDividendCalendarProjection projection = calendarInput(
            101L, new BigDecimal("10"), new BigDecimal("2700"),
            LocalDate.of(2026, 3, 15), 3);
        given(holdingRepository.findDividendCalendarInputsByUserId(1L))
            .willReturn(List.of(projection));

        // Result should be 27,000 (actual only), NOT 54,000 (actual + duplicate projection)
        assertThat(calculator.actualMonthlyDividend(1L, ym)).isEqualByComparingTo("27000");
    }

    private HoldingWithProduct holding(Long holdingId, Long productId, BigDecimal quantity, String accountType) {
        return new HoldingWithProduct() {
            @Override public Long getHoldingId() { return holdingId; }
            @Override public Long getAccountId() { return 1L; }
            @Override public Long getProductId() { return productId; }
            @Override public BigDecimal getEvaluationAmount() { return null; }
            @Override public BigDecimal getQuantity() { return quantity; }
            @Override public String getAccountType() { return accountType; }
        };
    }

    private HoldingDividendPaymentProjection payment(Long productId, BigDecimal quantity,
            BigDecimal amountPerUnit, LocalDate paymentDate) {
        return new HoldingDividendPaymentProjection() {
            @Override public Long getProductId() { return productId; }
            @Override public String getProductName() { return "ETF"; }
            @Override public BigDecimal getQuantity() { return quantity; }
            @Override public BigDecimal getAmountPerUnit() { return amountPerUnit; }
            @Override public LocalDate getPaymentDate() { return paymentDate; }
        };
    }

    private HoldingDividendCalendarProjection calendarInput(Long productId, BigDecimal quantity,
            BigDecimal amountPerUnit, LocalDate latestPaymentDate, int intervalMonths) {
        return new HoldingDividendCalendarProjection() {
            @Override public Long getProductId() { return productId; }
            @Override public String getProductName() { return "ETF"; }
            @Override public BigDecimal getQuantity() { return quantity; }
            @Override public BigDecimal getAmountPerUnit() { return amountPerUnit; }
            @Override public LocalDate getLatestPaymentDate() { return latestPaymentDate; }
            @Override public Integer getDistributionIntervalMonths() { return intervalMonths; }
        };
    }
}
