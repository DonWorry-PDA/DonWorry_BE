package com.sol.user.portfolio.calculator;

import com.sol.common.exception.BaseException;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResult;
import com.sol.user.portfolio.type.InvestmentPropensity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationGradeCalculatorTest {

    private final OperationGradeCalculator calculator = new OperationGradeCalculator();

    // ── 페르소나: 6억 (α충족 정상 케이스) ────────────────────────────────────

    @Test
    void 페르소나_6억_운용등급_정상산출() {
        OperationGradeInput input = baseBuilder()
                .totalAsset(BigDecimal.valueOf(600_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(500_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(1_200_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2).q2(1)
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getSurplus()).isPositive();
        assertThat(result.getCapabilityScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(result.getPreferenceScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(result.getFinalGrade()).isBetween(1, 3); // 위험중립형 cap=3
    }

    // ── 페르소나: 2.5억 (구조적 부족 — 여유분=0) ─────────────────────────────

    @Test
    void 페르소나_2억5천_여유분_0() {
        OperationGradeInput input = baseBuilder()
                .totalAsset(BigDecimal.valueOf(250_000_000))
                .pensionSaving(BigDecimal.valueOf(30_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(200_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(500_000))
                .hasLossInsurance(false)
                .hasMajorIllnessInsurance(false)
                .monthlyLoanRepayment(BigDecimal.valueOf(300_000))
                .q1(0).q2(0)
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getSurplus()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── 성향 cap 작동 검증 ─────────────────────────────────────────────────

    @Test
    void 성향cap_운용등급보다_성향이_낮으면_성향으로_제한() {
        OperationGradeInput input = baseBuilder()
                .totalAsset(BigDecimal.valueOf(1_000_000_000))
                .pensionSaving(BigDecimal.valueOf(100_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(900_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(2_000_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(3).q2(2)
                .investmentPropensity(InvestmentPropensity.STABLE) // 성향 cap=1
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getFinalGrade()).isEqualTo(1);
        assertThat(result.getOperationGrade()).isGreaterThanOrEqualTo(result.getFinalGrade());
    }

    // ── 능력 점수 각 지표 경계값 ──────────────────────────────────────────────

    @Test
    void 의료점수_보험없음_0점() {
        OperationGradeInput input = baseBuilder()
                .hasLossInsurance(false)
                .hasMajorIllnessInsurance(false)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getMedicalScore()).isEqualByComparingTo("0");
    }

    @Test
    void 의료점수_실손만_40점() {
        OperationGradeInput input = baseBuilder()
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(false)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getMedicalScore()).isEqualByComparingTo("40");
    }

    @Test
    void 의료점수_둘다_100점() {
        OperationGradeInput input = baseBuilder()
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getMedicalScore()).isEqualByComparingTo("100");
    }

    // ── 점수 0~100 clamp 검증 ─────────────────────────────────────────────

    @Test
    void 능력점수_clamp_음수_누수_없음() {
        OperationGradeInput input = baseBuilder()
                .monthlyNationalPension(BigDecimal.ZERO)
                .availableFinancialAsset(BigDecimal.ZERO)
                .monthlyLoanRepayment(BigDecimal.valueOf(10_000_000))
                .hasLossInsurance(false)
                .hasMajorIllnessInsurance(false)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getFloorScore()).isEqualByComparingTo("0");
        assertThat(result.getBufferScore()).isEqualByComparingTo("0");
        assertThat(result.getDebtScore()).isEqualByComparingTo("0");
        assertThat(result.getCapabilityScore()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // ── 운용 등급 경계값 ──────────────────────────────────────────────────

    @Test
    void 운용등급_점수20_정확히_2등급() {
        OperationGradeInput input = baseBuilder()
                .monthlyNationalPension(BigDecimal.valueOf(432_000))
                .q1(1).q2(0)
                .build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getOperationGrade()).isEqualTo(2);
        assertThat(result.getFinalGrade()).isBetween(1, result.getOperationGrade());
    }

    // ── 선호 점수 정규화 ───────────────────────────────────────────────────

    @Test
    void 선호점수_최대값_Q1_3_Q2_2() {
        OperationGradeInput input = baseBuilder().q1(3).q2(2).build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getPreferenceScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void 선호점수_최솟값_Q1_0_Q2_0() {
        OperationGradeInput input = baseBuilder().q1(0).q2(0).build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getPreferenceScore()).isEqualByComparingTo("0.00");
    }

    // ── 입력 검증 ─────────────────────────────────────────────────────────

    @Test
    void 목표생활비_0이면_예외() {
        assertThatThrownBy(() ->
                calculator.calculate(baseBuilder()
                        .targetMonthlyLivingCost(BigDecimal.ZERO)
                        .build()))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 필수비율_범위_초과하면_예외() {
        assertThatThrownBy(() ->
                calculator.calculate(baseBuilder()
                        .essentialRatio(new BigDecimal("0.90"))
                        .build()))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void Q1_범위_초과하면_예외() {
        assertThatThrownBy(() ->
                calculator.calculate(baseBuilder().q1(4).build()))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 남은햇수_상한_40년_초과_불가() {
        OperationGradeInput input = baseBuilder().age(30).build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getRemainingYears()).isLessThanOrEqualTo(40);
    }

    @Test
    void 남은햇수_하한_3년_미만_불가() {
        OperationGradeInput input = baseBuilder().age(95).build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getRemainingYears()).isGreaterThanOrEqualTo(3);
    }

    // 남은햇수 = clamp(lookupExpectancy(age) + 5, 3, 40). 5세 구간 경계 자체를 고정해 테이블 변경을 회귀로 잡는다.
    @ParameterizedTest
    @CsvSource({
            "30, 34",   // ≤55 구간
            "55, 34",
            "56, 30",   // 56~60
            "60, 30",
            "61, 26",   // 61~65
            "65, 26",
            "66, 22",   // 66~70
            "70, 22",
            "71, 18",   // 71~75
            "75, 18",
            "76, 15",   // 76~80
            "80, 15",
            "81, 12",   // 81~85
            "85, 12",
            "86, 10",   // 86~90
            "90, 10",
            "91, 8",    // 91 이상
            "95, 8"
    })
    void 남은햇수_연령구간_경계_회귀검증(int age, int expectedRemainingYears) {
        OperationGradeInput input = baseBuilder().age(age).build();

        OperationGradeResult result = calculator.calculate(input);

        assertThat(result.getRemainingYears()).isEqualTo(expectedRemainingYears);
    }

    // ── 공통 기본 입력 빌더 ───────────────────────────────────────────────

    private OperationGradeInput.OperationGradeInputBuilder baseBuilder() {
        return OperationGradeInput.builder()
                .age(65)
                .totalAsset(BigDecimal.valueOf(300_000_000))
                .pensionSaving(BigDecimal.valueOf(30_000_000))
                .pinnedSafeAsset(BigDecimal.ZERO)
                .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                .essentialRatio(new BigDecimal("0.72"))
                .monthlyNationalPension(BigDecimal.valueOf(1_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(250_000_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(false)
                .monthlyLoanRepayment(BigDecimal.valueOf(200_000))
                .q1(2)
                .q2(1)
                .investmentPropensity(InvestmentPropensity.NEUTRAL);
    }
}
