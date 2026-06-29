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

        // α는 "투자로 메워야 할 월 부족분" — 국민연금은 실수령(net) 기준으로 빼야 income·충족률과 일관.
        BigDecimal alpha = input.targetLivingCost()
                .subtract(netNationalPension(input.monthlyNationalPension()))
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
            // totalIncome/sustainableIncome은 net 국민연금을 포함하므로, 운용분만 떼낼 때도 net으로 빼야 일관.
            BigDecimal netNationalPension = netNationalPension(input.monthlyNationalPension());
            BigDecimal totalOp = c.totalIncome().subtract(netNationalPension);
            BigDecimal sustainableOp = c.sustainableIncome().subtract(netNationalPension);
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
                // 단기버킷은 "곧 빼 쓸 일회성 목돈" — 월수령 흐름도 상속도 아니라 별도 항으로 그대로 통과시킨다.
                // 인출 시점이 정해지지 않아 보유 중 이자는 과대표시 위험이 있으므로 원금만 노출(이자 무시).
                .shortTermLumpSum(money(shortTermLumpSum(plan)))
                .safeNetIncome(money(c.safeNetIncome()))
                .riskNetIncome(money(c.riskNetIncome()))
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

        BigDecimal dividendFrac = dividendFraction(plan.getPlanDividendRate());

        // ── 소진분/보존분 분리 ──
        // 소진분: 남은햇수에 걸쳐 인출 → 연금지급식(PMT)으로 "줄어드는 잔액 이자 + 원금 회수"를 함께 계산.
        //   (직선소진 + 전액이자로 더하던 기존식은 이미 쓴 원금에서 이자가 계속 나오는 이중계상이라 폐기)
        // 보존분: 영구 보유 → 이자/배당만 수입, 원금은 상속.
        BigDecimal riskDeplete = surplusRisk.multiply(ratio);
        BigDecimal safeDeplete = surplusSafe.multiply(ratio);
        BigDecimal pensionDeplete = canWithdrawPension ? pensionSaving.multiply(ratio) : BigDecimal.ZERO;
        BigDecimal riskPreserved = surplusRisk.subtract(riskDeplete);
        BigDecimal safePreserved = surplusSafe.subtract(safeDeplete);
        BigDecimal pensionPreserved = pensionSaving.subtract(pensionDeplete); // 인출 불가 시 전액 보존

        // 지속가능 월수령(원금 전액 보존 가정: 이자·배당만 — 소진/자본차익 제외). 전부 net 실수령.
        BigDecimal sustainable = netNationalPension(input.monthlyNationalPension())
                .add(netFinancial(monthlyYield(input.floorAsset(), PortfolioConstants.SAFE_RATE)))
                .add(netFinancial(monthlyYield(surplusSafe, PortfolioConstants.SAFE_RATE)))
                .add(netFinancial(monthlyYield(surplusRisk, dividendFrac)));
        if (canWithdrawPension) {
            sustainable = sustainable.add(
                    netPrivatePension(monthlyYield(pensionSaving, PortfolioConstants.PENSION_SAVING_RATE), input.age()));
        }

        // 총인출 월수령(소진 시나리오): 바닥·보존분은 이자/배당, 소진분은 연금지급식(PMT).
        //   위험 소진분은 총수익률(r=배당+자본차익)로 연금화 → 자본차익이 소진분 annuity에 내재된다.
        //   보존분 자본차익은 미실현(상속으로 귀속) → 기존 "전액 surplusRisk capgain" 이중계상 제거.
        // 전부 net 실수령. yield(이자·배당)는 전액 과세, 소진 annuity는 원금회수분 비과세·수익분만 과세,
        // 연금저축은 과세이연이라 yield·annuity 전액 연금소득세.
        //   #192: 위험 소진분 gain은 배당분(도미사일 무관 15.4%)/자본차익분(국내주식형 비과세)으로 분해 과세.
        // 버킷별 net 운용수입 — 종목별 monthlyContribution 분배 재료(#238). floor yield는 SAFE 종목 원금에
        //   floor가 섞여 있어 SAFE에 귀속(floor 포함). 국민연금·연금저축은 종목 아니므로 종목 귀속에서 제외.
        BigDecimal safeNet = netFinancial(monthlyYield(input.floorAsset(), PortfolioConstants.SAFE_RATE))
                .add(netFinancial(monthlyYield(safePreserved, PortfolioConstants.SAFE_RATE)))
                .add(netAnnuityFinancial(safeDeplete, PortfolioConstants.SAFE_RATE, years));
        BigDecimal riskNet = netFinancial(monthlyYield(riskPreserved, dividendFrac))
                .add(netRiskAnnuity(riskDeplete, dividendFrac, capGainTaxableWeight(plan), years));

        BigDecimal total = netNationalPension(input.monthlyNationalPension())
                .add(safeNet)
                .add(riskNet);
        if (canWithdrawPension) {
            total = total
                    .add(netPrivatePension(monthlyYield(pensionPreserved, PortfolioConstants.PENSION_SAVING_RATE), input.age()))
                    .add(netPrivatePension(monthlyAnnuity(pensionDeplete, PortfolioConstants.PENSION_SAVING_RATE, years), input.age()));
        }

        // 상속분 = 미소진 여유위험(실질 자본상승 복리) + 미소진 여유안전 + 미소진 연금저축.
        //   보존 위험분(안 파는 배당주)은 주가가 실질로 오른 만큼 물려줄 자산이 커진다.
        //   실질자본상승률 = max(총수익 r − 배당률 − 물가, 0). 배당주는 r−배당이 작아 ≈0(매우 보수),
        //   저배당 성장주만 (+). "무조건 우상향 아님"을 물가 차감 + 0클램프로 구조 반영.
        //   안전·연금 보존분은 수익(이자)을 이미 수입으로 빼갔으니 원금 그대로(중복성장 방지).
        BigDecimal realCapitalGainRate = PortfolioConstants.EXPECTED_TOTAL_RETURN
                .subtract(dividendFrac)
                .subtract(PortfolioConstants.EXPECTED_INFLATION)
                .max(BigDecimal.ZERO);
        BigDecimal riskInheritance = compound(riskPreserved, realCapitalGainRate, years);
        BigDecimal inheritance = riskInheritance.add(safePreserved).add(pensionPreserved);

        return new Computed(sustainable, total, inheritance, safeNet, riskNet);
    }

    /** 원금을 연 성장률로 N년 복리 성장시킨 값(상속가치용). rate≤0이면 원금 그대로. */
    private BigDecimal compound(BigDecimal principal, BigDecimal annualRate, BigDecimal years) {
        if (principal.signum() <= 0 || annualRate.signum() <= 0) {
            return principal.max(BigDecimal.ZERO);
        }
        int n = years.intValueExact();
        return principal.multiply(BigDecimal.ONE.add(annualRate).pow(n));
    }

    /** 이자·배당소득 원천징수(15.4%) 차감 후 실수령. yield(이자·배당)는 전액 과세대상. */
    private BigDecimal netFinancial(BigDecimal income) {
        return income.multiply(BigDecimal.ONE.subtract(PortfolioConstants.WITHHOLDING_FINANCIAL));
    }

    /** 국민연금 실수령 — 연금소득공제로 면세 근사(현재 0%). */
    private BigDecimal netNationalPension(BigDecimal income) {
        return income.multiply(BigDecimal.ONE.subtract(PortfolioConstants.NATIONAL_PENSION_TAX_RATE));
    }

    /** 사적연금(연금저축·IRP) 실수령 — 연령별 연금소득세. 과세이연이라 인출액 전액(원금+수익) 과세. */
    private BigDecimal netPrivatePension(BigDecimal income, int age) {
        return income.multiply(BigDecimal.ONE.subtract(PortfolioConstants.privatePensionTaxRate(age)));
    }

    /**
     * 소진분 annuity 실수령 — 원금회수분은 비과세(세후 원금이므로), 수익분(이자·자본차익)만 금융소득세.
     * 통째 과세하면 원금회수에도 세금이 붙어 "원금소진+상속+바닥=총자산" 보존 불변식이 깨진다.
     * 안전 소진분(채권=이자) 전용 — 위험 소진분은 자본차익 국내/해외 구분이 있어 netRiskAnnuity를 쓴다.
     */
    private BigDecimal netAnnuityFinancial(BigDecimal principal, BigDecimal annualRate, BigDecimal years) {
        if (principal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = monthlyAnnuity(principal, annualRate, years);
        BigDecimal principalBack = monthlyDepletion(principal, years);          // 비과세 원금회수분
        BigDecimal gain = gross.subtract(principalBack).max(BigDecimal.ZERO);   // 과세 수익분
        return principalBack.add(netFinancial(gain));
    }

    /**
     * 위험 소진분 annuity 실수령 — 원금회수분 비과세, gain(=배당+자본차익)을 구성비로 분해해 차등 과세.
     *   r=EXPECTED_TOTAL_RETURN(배당+자본차익). 배당분 = gain×(div/r): 분배금이라 도미사일 무관 15.4%.
     *   자본차익분 = gain−배당분: 국내주식형은 매매차익 비과세 → 과세분 가중(taxableWeight)만 15.4%.
     *   배당률이 r 이상이면 자본차익분 0(전액 배당과세). taxableWeight=1.0(전액 해외)이면
     *   netFinancial 선형성으로 기존 netAnnuityFinancial과 수학적으로 동일 → #192 이전 동작 보존.
     */
    private BigDecimal netRiskAnnuity(BigDecimal principal, BigDecimal dividendFrac,
                                      BigDecimal taxableWeight, BigDecimal years) {
        if (principal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal r = PortfolioConstants.EXPECTED_TOTAL_RETURN;                // > 0 보장(상수)
        BigDecimal gross = monthlyAnnuity(principal, r, years);
        BigDecimal principalBack = monthlyDepletion(principal, years);          // 비과세 원금회수분
        BigDecimal gain = gross.subtract(principalBack).max(BigDecimal.ZERO);   // 과세 대상 수익분(배당+자본차익)
        // 수익률 구성비로 분해. 배당률>r이면 분배분이 gain을 넘지 않도록 cap → 자본차익분 0.
        BigDecimal dividendShare = gain.multiply(dividendFrac)
                .divide(r, CALC_SCALE, RoundingMode.HALF_UP)
                .min(gain);
        BigDecimal capGainShare = gain.subtract(dividendShare);                 // ≥ 0
        BigDecimal taxedCapGain = capGainShare.multiply(taxableWeight);         // 해외분만 과세
        BigDecimal exemptCapGain = capGainShare.subtract(taxedCapGain);         // 국내주식형 비과세분
        return principalBack
                .add(netFinancial(dividendShare))
                .add(netFinancial(taxedCapGain))
                .add(exemptCapGain);
    }

    /** 위험버킷 자본차익 과세분 가중(null=전액 과세 1.0 폴백 → #192 이전 동작·기존 테스트 보존). */
    private BigDecimal capGainTaxableWeight(PlanAllocation plan) {
        BigDecimal weight = plan.getRiskCapGainTaxableWeight();
        return weight == null ? BigDecimal.ONE : weight;
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

    /**
     * 소진분 연금지급식 월수령. 연 PMT = PV·r / (1 − (1+r)^−N), 월수령 = 연PMT / 12.
     * "줄어드는 잔액에서 받는 이자 + 원금 회수"를 한 식으로 합쳐, 직선소진+전액이자 이중계상을 제거한다.
     * r은 자산별 총수익률(안전 SAFE_RATE, 위험 EXPECTED_TOTAL_RETURN, 연금 PENSION_SAVING_RATE).
     */
    private BigDecimal monthlyAnnuity(BigDecimal principal, BigDecimal annualRate, BigDecimal years) {
        if (principal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (annualRate.signum() <= 0) {
            return monthlyDepletion(principal, years); // 이율 0이면 단순 직선소진으로 폴백
        }
        int n = years.intValueExact();
        BigDecimal discountFactor = BigDecimal.ONE
                .divide(BigDecimal.ONE.add(annualRate).pow(n), CALC_SCALE, RoundingMode.HALF_UP);
        BigDecimal annualPmt = principal.multiply(annualRate)
                .divide(BigDecimal.ONE.subtract(discountFactor), CALC_SCALE, RoundingMode.HALF_UP);
        return annualPmt.divide(TWELVE, CALC_SCALE, RoundingMode.HALF_UP);
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

    /** 단기 목돈 = 단기버킷 원금 그대로. 미설정(null)이면 0. */
    private BigDecimal shortTermLumpSum(PlanAllocation plan) {
        return plan.getShortTermBucket() == null ? BigDecimal.ZERO : plan.getShortTermBucket();
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

    private record Computed(BigDecimal sustainableIncome, BigDecimal totalIncome, BigDecimal inheritance,
                            BigDecimal safeNetIncome, BigDecimal riskNetIncome) {
    }
}
