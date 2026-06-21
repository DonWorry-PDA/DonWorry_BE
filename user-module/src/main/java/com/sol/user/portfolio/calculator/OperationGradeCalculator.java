package com.sol.user.portfolio.calculator;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResult;
import com.sol.user.portfolio.type.Gender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OperationGradeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 4;

    private static final BigDecimal FLOOR_WEIGHT   = new BigDecimal("0.45");
    private static final BigDecimal BUFFER_WEIGHT  = new BigDecimal("0.25");
    private static final BigDecimal MEDICAL_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal DEBT_WEIGHT    = new BigDecimal("0.10");

    private static final BigDecimal CAPABILITY_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal PREFERENCE_WEIGHT = new BigDecimal("0.4");

    private static final BigDecimal DSR_LIMIT    = new BigDecimal("0.40");
    private static final BigDecimal BUFFER_YEARS = BigDecimal.valueOf(5);

    public OperationGradeResult calculate(OperationGradeInput input) {
        validate(input);

        // STEP1 — 바닥자산 & 여유분
        int remainingYears = calcRemainingYears(input.age(), input.gender());
        BigDecimal essentialLivingCost = input.targetMonthlyLivingCost()
                .multiply(input.essentialRatio());
        BigDecimal floorAsset = calcFloorAsset(input.monthlyNationalPension(), essentialLivingCost, remainingYears);
        BigDecimal availableAsset = input.totalAsset().subtract(input.pensionSaving());
        BigDecimal surplus = availableAsset.subtract(floorAsset).max(BigDecimal.ZERO);

        // STEP2 — 능력 점수
        BigDecimal floorScore   = calcFloorScore(input.monthlyNationalPension(), essentialLivingCost);
        BigDecimal bufferScore  = calcBufferScore(input, floorAsset);
        BigDecimal medicalScore = calcMedicalScore(input);
        BigDecimal debtScore    = calcDebtScore(input);
        BigDecimal capabilityScore = floorScore.multiply(FLOOR_WEIGHT)
                .add(bufferScore.multiply(BUFFER_WEIGHT))
                .add(medicalScore.multiply(MEDICAL_WEIGHT))
                .add(debtScore.multiply(DEBT_WEIGHT));

        // STEP3 — 선호 점수
        BigDecimal preferenceScore = calcPreferenceScore(input);

        // STEP4 — 운용 등급 + 성향 cap
        BigDecimal operationScore = capabilityScore.multiply(CAPABILITY_WEIGHT)
                .add(preferenceScore.multiply(PREFERENCE_WEIGHT));
        int operationGrade = toOperationGrade(operationScore);
        int finalGrade = Math.min(operationGrade, input.investmentPropensity().getGradeLimit());

        return OperationGradeResult.builder()
                .remainingYears(remainingYears)
                .floorAsset(floorAsset)
                .availableAsset(availableAsset)
                .surplus(surplus)
                .floorScore(round(floorScore))
                .bufferScore(round(bufferScore))
                .medicalScore(round(medicalScore))
                .debtScore(round(debtScore))
                .capabilityScore(round(capabilityScore))
                .preferenceScore(round(preferenceScore))
                .operationScore(round(operationScore))
                .operationGrade(operationGrade)
                .finalGrade(finalGrade)
                .build();
    }

    // ── STEP1 ────────────────────────────────────────────────────────────────

    private int calcRemainingYears(int age, Gender gender) {
        int expectancy = gender == Gender.MALE ? lookupMale(age) : lookupFemale(age);
        return Math.min(Math.max(expectancy + 5, 3), 40);
    }

    // 통계청 2022 생명표 기준 기대여명 (5세 단위)
    private int lookupMale(int age) {
        if (age <= 55) return 26;
        if (age <= 60) return 22;
        if (age <= 65) return 18;
        if (age <= 70) return 15;
        if (age <= 75) return 11;
        if (age <= 80) return 8;
        if (age <= 85) return 6;
        if (age <= 90) return 4;
        return 3;
    }

    private int lookupFemale(int age) {
        if (age <= 55) return 31;
        if (age <= 60) return 27;
        if (age <= 65) return 23;
        if (age <= 70) return 19;
        if (age <= 75) return 14;
        if (age <= 80) return 11;
        if (age <= 85) return 8;
        if (age <= 90) return 5;
        return 3;
    }

    private BigDecimal calcFloorAsset(BigDecimal monthlyNationalPension,
                                      BigDecimal essentialLivingCost,
                                      int remainingYears) {
        BigDecimal monthlyShortage = essentialLivingCost.subtract(monthlyNationalPension)
                .max(BigDecimal.ZERO);
        return monthlyShortage.multiply(BigDecimal.valueOf(12L * remainingYears));
    }

    // ── STEP2 ────────────────────────────────────────────────────────────────

    private BigDecimal calcFloorScore(BigDecimal monthlyNationalPension, BigDecimal essentialLivingCost) {
        return clamp(monthlyNationalPension.multiply(HUNDRED)
                .divide(essentialLivingCost, SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal calcBufferScore(OperationGradeInput input, BigDecimal floorAsset) {
        // 연재량지출 = 목표생활비 × (1 − 필수비율) × 12
        BigDecimal discretionaryAnnual = input.targetMonthlyLivingCost()
                .multiply(BigDecimal.ONE.subtract(input.essentialRatio()))
                .multiply(BigDecimal.valueOf(12));
        BigDecimal numerator = input.availableFinancialAsset().subtract(floorAsset);
        return clamp(numerator.multiply(HUNDRED)
                .divide(discretionaryAnnual.multiply(BUFFER_YEARS), SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal calcMedicalScore(OperationGradeInput input) {
        int score = (input.hasLossInsurance() ? 40 : 0)
                  + (input.hasMajorIllnessInsurance() ? 60 : 0);
        return BigDecimal.valueOf(score);
    }

    private BigDecimal calcDebtScore(OperationGradeInput input) {
        BigDecimal denom = input.targetMonthlyLivingCost().multiply(DSR_LIMIT);
        BigDecimal ratio = input.monthlyLoanRepayment()
                .divide(denom, SCALE, RoundingMode.HALF_UP);
        return clamp(BigDecimal.ONE.subtract(ratio).multiply(HUNDRED));
    }

    // ── STEP3 ────────────────────────────────────────────────────────────────

    private BigDecimal calcPreferenceScore(OperationGradeInput input) {
        BigDecimal q1Norm = BigDecimal.valueOf(input.q1())
                .divide(BigDecimal.valueOf(3), SCALE, RoundingMode.HALF_UP);
        BigDecimal q2Norm = BigDecimal.valueOf(input.q2())
                .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
        return q1Norm.multiply(new BigDecimal("0.6"))
                .add(q2Norm.multiply(new BigDecimal("0.4")))
                .multiply(HUNDRED);
    }

    // ── STEP4 ────────────────────────────────────────────────────────────────

    // 경계값: [0,20)→1, [20,40)→2, [40,60)→3, [60,80)→4, [80,100]→5
    private int toOperationGrade(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(20)) < 0) return 1;
        if (score.compareTo(BigDecimal.valueOf(40)) < 0) return 2;
        if (score.compareTo(BigDecimal.valueOf(60)) < 0) return 3;
        if (score.compareTo(BigDecimal.valueOf(80)) < 0) return 4;
        return 5;
    }

    // ── 공통 유틸 ─────────────────────────────────────────────────────────────

    private BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(HUNDRED);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    // ── 입력 검증 ─────────────────────────────────────────────────────────────

    private void validate(OperationGradeInput input) {
        if (input == null
                || input.gender() == null
                || input.investmentPropensity() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        requirePositive(input.targetMonthlyLivingCost());
        requireInRange(input.essentialRatio(), new BigDecimal("0.60"), new BigDecimal("0.85"));
        requireNonNegative(input.monthlyNationalPension());
        requireNonNegative(input.totalAsset());
        requireNonNegative(input.pensionSaving());
        requireNonNegative(input.availableFinancialAsset());
        requireNonNegative(input.monthlyLoanRepayment());
        if (input.q1() < 0 || input.q1() > 3) throw new BaseException(ErrorCode.INVALID_INPUT);
        if (input.q2() < 0 || input.q2() > 2) throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    private void requirePositive(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireNonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireInRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null
                || value.compareTo(min) < 0
                || value.compareTo(max) > 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }
}
