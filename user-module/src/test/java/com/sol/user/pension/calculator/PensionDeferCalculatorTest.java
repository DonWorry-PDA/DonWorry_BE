package com.sol.user.pension.calculator;

import com.sol.user.pension.dto.PensionDeferComparisonRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class PensionDeferCalculatorTest {

    private PensionDeferCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PensionDeferCalculator();
    }

    @Test
    @DisplayName("deferRate=0 → 기존 연금 전액 수령")
    void calcDuringDeferMonthly_0percent() {
        assertThat(calculator.calcDuringDeferMonthly(BigDecimal.valueOf(1_000_000), 0))
            .isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("deferRate=50 → 기존 연금 50% 수령")
    void calcDuringDeferMonthly_50percent() {
        assertThat(calculator.calcDuringDeferMonthly(BigDecimal.valueOf(1_000_000), 50))
            .isEqualTo(500_000L);
    }

    @Test
    @DisplayName("deferRate=100 → 연기 기간 중 0원")
    void calcDuringDeferMonthly_100percent() {
        assertThat(calculator.calcDuringDeferMonthly(BigDecimal.valueOf(1_000_000), 100))
            .isEqualTo(0L);
    }

    @Test
    @DisplayName("deferRate=50, deferYears=1, base=100만 → 103.6만원 (명세 예시)")
    void calcAfterDeferMonthly_50percent_1year() {
        assertThat(calculator.calcAfterDeferMonthly(BigDecimal.valueOf(1_000_000), 50, 1))
            .isEqualTo(1_036_000L);
    }

    @Test
    @DisplayName("deferRate=100, deferYears=1, base=100만 → 107.2만원 (명세 예시)")
    void calcAfterDeferMonthly_100percent_1year() {
        assertThat(calculator.calcAfterDeferMonthly(BigDecimal.valueOf(1_000_000), 100, 1))
            .isEqualTo(1_072_000L);
    }

    @Test
    @DisplayName("deferRate=70, deferYears=5, base=120만 → 1,502,400원")
    void calcAfterDeferMonthly_70percent_5years() {
        assertThat(calculator.calcAfterDeferMonthly(BigDecimal.valueOf(1_200_000), 70, 5))
            .isEqualTo(1_502_400L);
    }

    @Test
    @DisplayName("deferRate=0 → 연기 없음, 기존 연금 그대로")
    void calcAfterDeferMonthly_0percent() {
        assertThat(calculator.calcAfterDeferMonthly(BigDecimal.valueOf(1_000_000), 0, 5))
            .isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("deferRate=70, deferYears=5 → 손익분기점 167개월 (올림)")
    void calcBreakEvenMonths_70percent_5years() {
        long after = calculator.calcAfterDeferMonthly(BigDecimal.valueOf(1_200_000), 70, 5);
        assertThat(calculator.calcBreakEvenMonths(BigDecimal.valueOf(1_200_000), 70, 5, after))
            .isEqualTo(167L);
    }

    @Test
    @DisplayName("충당률: (130만 / 220만) * 100 = 59 (소수점 버림)")
    void calcCoverageRate_floors() {
        assertThat(calculator.calcCoverageRate(
            BigDecimal.valueOf(1_200_000),
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(2_200_000)
        )).isEqualTo(59);
    }

    @Test
    @DisplayName("충당률 70 이상 → 안정")
    void toStabilityLabel_70orAbove() {
        assertThat(calculator.toStabilityLabel(70)).isEqualTo("안정");
        assertThat(calculator.toStabilityLabel(100)).isEqualTo("안정");
    }

    @Test
    @DisplayName("충당률 69 이하 → 주의")
    void toStabilityLabel_below70() {
        assertThat(calculator.toStabilityLabel(69)).isEqualTo("주의");
        assertThat(calculator.toStabilityLabel(0)).isEqualTo("주의");
    }

    @Test
    @DisplayName("calcAllRows: 7행, [0,50,60,70,80,90,100] 순서")
    void calcAllRows_returns7RowsInOrder() {
        List<PensionDeferComparisonRow> rows = calculator.calcAllRows(
            BigDecimal.valueOf(1_200_000), 5,
            BigDecimal.valueOf(100_000), BigDecimal.valueOf(2_200_000)
        );
        assertThat(rows).hasSize(7);
        assertThat(rows.stream().map(PensionDeferComparisonRow::deferRate).collect(Collectors.toList()))
            .containsExactly(0, 50, 60, 70, 80, 90, 100);
    }

    @Test
    @DisplayName("calcAllRows: deferRate=0 행은 breakEvenMonths=null")
    void calcAllRows_deferRate0_nullBreakEven() {
        List<PensionDeferComparisonRow> rows = calculator.calcAllRows(
            BigDecimal.valueOf(1_200_000), 5,
            BigDecimal.valueOf(100_000), BigDecimal.valueOf(2_200_000)
        );
        assertThat(rows.get(0).breakEvenMonths()).isNull();
    }

    @Test
    @DisplayName("deferRate=0 인사이트: '즉시 수령' 포함")
    void generateInsight_deferRate0() {
        List<PensionDeferComparisonRow> rows = calculator.calcAllRows(
            BigDecimal.valueOf(1_200_000), 5,
            BigDecimal.valueOf(100_000), BigDecimal.valueOf(2_200_000)
        );
        assertThat(calculator.generateInsight(0, rows)).contains("즉시 수령");
    }

    @Test
    @DisplayName("deferRate=100 인사이트: '가장 많이 증가' 포함")
    void generateInsight_deferRate100() {
        List<PensionDeferComparisonRow> rows = calculator.calcAllRows(
            BigDecimal.valueOf(1_200_000), 5,
            BigDecimal.valueOf(100_000), BigDecimal.valueOf(2_200_000)
        );
        assertThat(calculator.generateInsight(100, rows)).contains("가장 많이 증가");
    }
}
