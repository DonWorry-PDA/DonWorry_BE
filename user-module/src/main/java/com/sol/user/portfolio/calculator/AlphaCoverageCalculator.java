package com.sol.user.portfolio.calculator;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.CoverageInput;
import com.sol.user.portfolio.dto.CoverageResult;
import com.sol.user.portfolio.dto.PlanAllocation;
import com.sol.user.portfolio.dto.PlanCoverage;
import com.sol.user.portfolio.dto.Q3Scenario;
import com.sol.user.portfolio.type.GuidanceBand;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * STEP6 — α 충족률 + 소진모델(바닥 보존 + 여유 소진). 상품데이터 무관 순수 수치계산.
 * 배당률만 STEP5가 넘긴 plan별 가중평균(DB, %단위)을 사용하고, 안전금리·연금저축수익률은 상수.
 */
@Component
public class AlphaCoverageCalculator {

    private static final int CALC_SCALE = 6;
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 2;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DIVIDEND_FRACTION_CEIL = new BigDecimal("0.2"); // 배당률 분수 상한(단위오류 가드)

    public CoverageResult calculate(CoverageInput input) {
        validate(input);

        BigDecimal alpha = input.targetLivingCost()
                .subtract(input.monthlyNationalPension())
                .subtract(input.otherRegularIncome());

        // ── 트랙 확정 — 구조적부족 우선(순서로 방어), 그 다음 α≤0(연금초과), 아니면 NORMAL ──
        // ※ 두 트랙은 기타정기수입>0이면 상호배타가 아님 → 반드시 이 순서로 판정.
        if (input.allocation().getTrack() == RecommendationTrack.STRUCTURAL_SHORTAGE) {
            return CoverageResult.builder()
                    .track(RecommendationTrack.STRUCTURAL_SHORTAGE)
                    .alpha(money(alpha))
                    .band(null)
                    .planCoverages(List.of())
                    .q3Scenarios(List.of())
                    .build();
        }

        boolean pensionSufficient = alpha.compareTo(BigDecimal.ZERO) <= 0;
        RecommendationTrack track = pensionSufficient
                ? RecommendationTrack.PENSION_SUFFICIENT
                : RecommendationTrack.NORMAL;

        // 안별 coverage (주어진 q3 기준)
        List<PlanCoverage> planCoverages = input.allocation().getPlans().stream()
                .map(plan -> toPlanCoverage(plan, input, alpha))
                .toList();

        // 안내 구간 band — NORMAL에서 안별 α충족률 최댓값 기준
        GuidanceBand band = track == RecommendationTrack.NORMAL
                ? GuidanceBand.from(maxCoverageRate(planCoverages))
                : null;

        // Q3 트레이드오프 표 — 대표안(안정안) 기준 3옵션
        List<Q3Scenario> q3Scenarios = buildQ3Scenarios(input);

        return CoverageResult.builder()
                .track(track)
                .alpha(money(alpha))
                .band(band)
                .planCoverages(planCoverages)
                .q3Scenarios(q3Scenarios)
                .build();
    }

    private PlanCoverage toPlanCoverage(PlanAllocation plan, CoverageInput input, BigDecimal alpha) {
        Computed c = compute(plan, input.q3(), input);
        BigDecimal alphaCoverageRate = null;
        BigDecimal sustainableCoverageRate = null;
        if (alpha.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalOp = c.totalIncome().subtract(input.monthlyNationalPension());
            BigDecimal sustainableOp = c.sustainableIncome().subtract(input.monthlyNationalPension());
            alphaCoverageRate = totalOp.multiply(HUNDRED)
                    .divide(alpha, RATE_SCALE, RoundingMode.HALF_UP)
                    .min(HUNDRED);
            sustainableCoverageRate = sustainableOp.multiply(HUNDRED)
                    .divide(alpha, RATE_SCALE, RoundingMode.HALF_UP)
                    .min(HUNDRED);
        }
        return PlanCoverage.builder()
                .type(plan.getType())
                .monthlyIncome(money(c.totalIncome()))
                .alphaCoverageRate(alphaCoverageRate)
                .sustainableCoverageRate(sustainableCoverageRate)
                .inheritanceAmount(money(c.inheritance()))
                .build();
    }

    private List<Q3Scenario> buildQ3Scenarios(CoverageInput input) {
        List<PlanAllocation> plans = input.allocation().getPlans();
        if (plans.isEmpty()) {
            return List.of();
        }
        // Q3 표는 "안정안 기준" 계약 — STABLE이 없으면 임의 안으로 대체하지 않고 계약 위반으로 처리.
        // (두 tier 모두 PLANS_BY_TIER에 STABLE 포함이라 정상 경로에선 항상 존재)
        PlanAllocation reference = plans.stream()
                .filter(p -> p.getType() == PlanType.STABLE)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));
        return List.of(0, 1, 2).stream()
                .map(q3 -> {
                    Computed c = compute(reference, q3, input);
                    return new Q3Scenario(q3, money(c.totalIncome()), money(c.inheritance()));
                })
                .toList();
    }

    /**
     * 월수령·상속 핵심 계산. 바닥자산은 원금 보존(이자만), 여유분은 소진비율만큼 원금 인출.
     */
    private Computed compute(PlanAllocation plan, int q3, CoverageInput input) {
        BigDecimal ratio = PortfolioConstants.depletionRatio(q3);
        BigDecimal years = BigDecimal.valueOf(input.remainingYears());

        BigDecimal surplusRisk = plan.getSurplusRiskAmount();
        BigDecimal surplusSafe = plan.getSurplusSafeAmount();
        BigDecimal pensionSaving = input.pensionSaving();
        // 55세 미만이면 연금저축 인출 불가 → 원금소진·수익 모두 0, 전액 상속
        boolean canWithdrawPension = input.age() >= PortfolioConstants.PENSION_WITHDRAWAL_MIN_AGE;

        // ── 원금소진액(세 자산 동일 소진비율) — 상속분/월수령에 함께 쓰이므로 한 곳에 모음 ──
        BigDecimal riskDeplete = surplusRisk.multiply(ratio);
        BigDecimal safeDeplete = surplusSafe.multiply(ratio);
        BigDecimal pensionDeplete = canWithdrawPension ? pensionSaving.multiply(ratio) : BigDecimal.ZERO;

        BigDecimal dividendFrac = dividendFraction(plan.getPlanDividendRate());
        // 자본차익률 = max(r − 배당률, 0). 파생값(독립 계수 아님), 음수=자본잠식이라 clamp.
        BigDecimal capitalGainRate = PortfolioConstants.EXPECTED_TOTAL_RETURN.subtract(dividendFrac).max(BigDecimal.ZERO);

        // 지속가능 월수령(원금 보존: 이자·배당만 — 원금소진·자본차익실현 제외)
        BigDecimal sustainable = input.monthlyNationalPension()
                .add(monthlyYield(input.floorAsset(), PortfolioConstants.SAFE_RATE))
                .add(monthlyYield(surplusSafe, PortfolioConstants.SAFE_RATE))
                .add(monthlyYield(surplusRisk, dividendFrac));
        if (canWithdrawPension) {
            sustainable = sustainable.add(monthlyYield(pensionSaving, PortfolioConstants.PENSION_SAVING_RATE));
        }

        // 총인출 월수령 = 지속가능 + 원금소진(안전·위험) + 위험 자본차익 실현 + 연금소진
        BigDecimal total = sustainable
                .add(monthlyDepletion(safeDeplete, years))
                .add(monthlyDepletion(riskDeplete, years))
                .add(monthlyCapitalGain(surplusRisk, capitalGainRate, years));
        if (canWithdrawPension) {
            total = total.add(monthlyDepletion(pensionDeplete, years));
        }

        // 상속분 = 미소진 여유위험 + 미소진 여유안전 + 미소진 연금저축 (자산 보존 일관성 위해 연금저축 포함)
        BigDecimal inheritance = surplusRisk.subtract(riskDeplete)
                .add(surplusSafe.subtract(safeDeplete))
                .add(pensionSaving.subtract(pensionDeplete));

        return new Computed(sustainable, total, inheritance);
    }

    /** 연 수익(원금 × 연이율)을 월로. rate는 분수(0.035). */
    private BigDecimal monthlyYield(BigDecimal principal, BigDecimal annualRate) {
        return principal.multiply(annualRate).divide(TWELVE, CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** 원금소진액을 남은햇수에 걸쳐 월로 분할. */
    private BigDecimal monthlyDepletion(BigDecimal depleteAmount, BigDecimal years) {
        return depleteAmount.divide(years, CALC_SCALE, RoundingMode.HALF_UP)
                .divide(TWELVE, CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** 위험 여유분 자본차익 실현분: 원금×((1+cg)^N − 1)을 N년에 걸쳐 월로. cg=자본차익률(분수, 1차 단순식). */
    private BigDecimal monthlyCapitalGain(BigDecimal principal, BigDecimal capitalGainRate, BigDecimal years) {
        if (principal.signum() <= 0 || capitalGainRate.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int n = years.intValueExact();
        BigDecimal growth = BigDecimal.ONE.add(capitalGainRate).pow(n).subtract(BigDecimal.ONE);
        return principal.multiply(growth)
                .divide(years, CALC_SCALE, RoundingMode.HALF_UP)
                .divide(TWELVE, CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** 배당률(plan 가중평균, %단위 예: 2.7750)을 분수로 변환. */
    private BigDecimal dividendFraction(BigDecimal dividendRatePercent) {
        if (dividendRatePercent == null) {
            // 상위(STEP5) 데이터 누락을 0으로 삼키면 월수령이 과소계산됨 → fail-fast
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        BigDecimal fraction = dividendRatePercent.divide(HUNDRED, CALC_SCALE, RoundingMode.HALF_UP);
        // 단위 불일치(%↔분수)·범위 이탈 조용한 오류 방지 — 배당률 분수는 [0, 0.2) 범위여야 함
        if (fraction.signum() < 0 || fraction.compareTo(DIVIDEND_FRACTION_CEIL) >= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        return fraction;
    }

    private BigDecimal maxCoverageRate(List<PlanCoverage> coverages) {
        return coverages.stream()
                .map(PlanCoverage::getAlphaCoverageRate)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::max);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validate(CoverageInput input) {
        if (input == null || input.allocation() == null || input.allocation().getTrack() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        // plans는 null 불가(구조적부족이면 빈 리스트 — 허용). 비어있지 않으면 각 안 필드 검증.
        if (input.allocation().getPlans() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        for (PlanAllocation plan : input.allocation().getPlans()) {
            if (plan == null || plan.getType() == null || plan.getPlanDividendRate() == null) {
                throw new BaseException(ErrorCode.INVALID_INPUT);
            }
            requireNonNegative(plan.getSurplusRiskAmount());
            requireNonNegative(plan.getSurplusSafeAmount());
        }
        if (input.q3() < 0 || input.q3() > 2) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        if (input.remainingYears() <= 0 || input.age() < 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        requireNonNegative(input.targetLivingCost());
        requireNonNegative(input.monthlyNationalPension());
        requireNonNegative(input.otherRegularIncome());
        requireNonNegative(input.floorAsset());
        requireNonNegative(input.pensionSaving());
    }

    private void requireNonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private record Computed(BigDecimal sustainableIncome, BigDecimal totalIncome, BigDecimal inheritance) {
    }
}
