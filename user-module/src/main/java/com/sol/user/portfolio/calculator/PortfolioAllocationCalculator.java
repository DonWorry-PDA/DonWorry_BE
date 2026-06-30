package com.sol.user.portfolio.calculator;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.AllocationInput;
import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.Holding;
import com.sol.user.portfolio.dto.PlanAllocation;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.PropensityTier;
import com.sol.user.portfolio.type.RecommendationTrack;
import com.sol.user.portfolio.type.SafeSlotWeight;
import com.sol.user.portfolio.type.SlotWeight;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * STEP5 — 여유분 기반 위험/안전 배분 + 3안(또는 위험중립형 2안) 산출. 순수 함수(상품조회는 호출 측에서).
 */
@Component
public class PortfolioAllocationCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;
    private static final BigDecimal DEFAULT_SHORT_TERM_RATIO = new BigDecimal("0.10");

    public AllocationResult calculate(AllocationInput input) {
        validate(input);

        // 구조적 부족 트랙 — 여유분<=0이면 3안 스킵
        if (input.surplus().compareTo(BigDecimal.ZERO) <= 0) {
            return AllocationResult.builder()
                    .track(RecommendationTrack.STRUCTURAL_SHORTAGE)
                    .plans(List.of())
                    .build();
        }

        // 일관성 불변식(여유분>0 확정 후) — 바닥자산은 가용자산(총자산−연금저축)을 넘을 수 없음.
        // 위반 시 오케스트레이터가 여유분을 일관되지 않게 넘긴 것 → 안전목표<바닥자산 방지.
        if (input.floorAsset().compareTo(input.totalAsset().subtract(input.pensionSaving())) > 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        PropensityTier tier = PropensityTier.from(input.propensity());
        Map<String, EtfInfo> byTicker = input.pool().stream()
                .collect(Collectors.toMap(EtfInfo::ticker, Function.identity(), (a, b) -> a));

        List<PlanAllocation> plans = PortfolioConstants.PLANS_BY_TIER.get(tier).stream()
                .map(plan -> buildPlan(plan, input, tier, byTicker))
                .toList();

        return AllocationResult.builder()
                .track(RecommendationTrack.NORMAL)
                .plans(plans)
                .build();
    }

    private PlanAllocation buildPlan(PlanType plan, AllocationInput input,
                                     PropensityTier tier, Map<String, EtfInfo> byTicker) {
        BigDecimal surplus = input.surplus();

        // 단기버킷: 유동성안만 선확보 (입력값 없으면 여유분×0.10), 여유분 초과 방지
        BigDecimal shortTermBucket = plan == PlanType.LIQUIDITY
                ? resolveShortTermBucket(input).min(surplus)
                : BigDecimal.ZERO;

        // 위험목표 = (여유분 − 단기버킷) × 위험비중[등급][안]
        BigDecimal riskBase = surplus.subtract(shortTermBucket);
        BigDecimal riskTarget = riskBase.multiply(PortfolioConstants.riskWeight(input.finalGrade(), plan));

        // 바닥보호 — 안전목표가 바닥자산 밑으로 내려가면 위험을 강제 하향(0까지)
        BigDecimal maxRiskForFloor = input.totalAsset()
                .subtract(input.floorAsset())
                .subtract(input.pensionSaving())
                .subtract(shortTermBucket);
        riskTarget = riskTarget.min(maxRiskForFloor).max(BigDecimal.ZERO);

        // 위험버킷 — 배당률은 위험 holding만으로 가중평균(STEP6 입력, 안전·단기 섞이면 오염되므로 먼저 계산)
        // #118: 권유가능등급 필터로 성향상 부적합한 위험 코어 슬롯은 제외되므로,
        // 실제 배분된 위험액으로 riskTarget을 재계산한다(제외분은 아래 safeTarget이 잔여로 흡수).
        List<Holding> riskHoldings = buildRiskHoldings(plan, tier, input.propensity(), riskTarget, byTicker);
        riskTarget = riskHoldings.stream()
                .map(Holding::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal planDividendRate = weightedDividendRate(riskHoldings, byTicker);
        BigDecimal riskCapGainTaxableWeight = weightedCapGainTaxableWeight(riskHoldings, byTicker);

        // 정기예금(pinnedSafe)은 ETF 매수 대상이 아니므로 매수 가능 안전버킷에서 제외.
        BigDecimal safeTarget = input.totalAsset()
                .subtract(riskTarget)
                .subtract(input.pensionSaving())
                .subtract(shortTermBucket)
                .subtract(input.pinnedSafe());

        // STEP6용 분리값
        BigDecimal surplusRiskAmount = riskTarget;
        BigDecimal surplusSafeAmount = surplus.subtract(riskTarget).subtract(shortTermBucket).max(BigDecimal.ZERO);

        // 안전버킷 — 바닥(원금보존)/여유안전(소진) 2층. 합이 정확히 safeTarget이 되도록 여유안전분=safeTarget−바닥자산.
        // 바닥보호 불변식상 safeTarget >= floorAsset이 보장되나, 입력 불일치 시에도 "안전합=safeTarget" 계약이
        // 깨지지 않도록 floorAsset을 safeTarget 상한으로 방어적 캡(정상 입력에선 floorAsset 그대로).
        BigDecimal floorAsset = input.floorAsset().min(safeTarget).max(BigDecimal.ZERO);
        BigDecimal surplusSafePortion = safeTarget.subtract(floorAsset).max(BigDecimal.ZERO);
        List<Holding> safeHoldings = buildSafeHoldings(floorAsset, surplusSafePortion, byTicker);
        List<Holding> shortTermHoldings = buildShortTermHoldings(shortTermBucket, byTicker);

        // 화면용 전체 보유: 안전 → 위험 → 단기 순(안정→성장→현금)
        List<Holding> holdings = new ArrayList<>();
        holdings.addAll(safeHoldings);
        holdings.addAll(riskHoldings);
        holdings.addAll(shortTermHoldings);

        return PlanAllocation.builder()
                .type(plan)
                .riskTarget(money(riskTarget))
                .safeTarget(money(safeTarget))
                .surplusRiskAmount(money(surplusRiskAmount))
                .surplusSafeAmount(money(surplusSafeAmount))
                .shortTermBucket(money(shortTermBucket))
                .planDividendRate(planDividendRate)
                .riskCapGainTaxableWeight(riskCapGainTaxableWeight)
                .holdings(holdings)
                .build();
    }

    private List<Holding> buildRiskHoldings(PlanType plan, PropensityTier tier, InvestmentPropensity propensity,
                                            BigDecimal riskTarget, Map<String, EtfInfo> byTicker) {
        if (riskTarget.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<Holding> holdings = new ArrayList<>();
        for (SlotWeight sw : PortfolioConstants.PLAN_COMPOSITION.get(plan)) {
            String ticker = PortfolioConstants.resolveTicker(tier, sw.slot());
            EtfInfo etf = byTicker.get(ticker);
            if (etf == null) {
                throw new BaseException(ErrorCode.INVALID_INPUT); // 풀에 필수 코어 종목 누락
            }
            // #118: 성향상 권유 불가한 위험등급이면 이 슬롯을 제외 → 해당 금액은 호출 측에서 안전버킷이 흡수.
            // 슬롯 비중은 재정규화하지 않아(reduce-to-safe), 적합성상 못 담는 위험을 남은 위험상품에 몰지 않는다.
            if (!PortfolioConstants.isRecommendable(propensity, etf.riskGrade())) {
                continue;
            }
            holdings.add(Holding.of(
                    etf.productId(),
                    etf.ticker(),
                    etf.productName(),
                    BucketRole.RISK,
                    etf.currency(),
                    sw.weight(),
                    money(riskTarget.multiply(sw.weight()))
            ));
        }
        return holdings;
    }

    /**
     * 안전버킷 개별 종목 — 바닥/여유안전 두 sub-bucket을 각 구성비로 배분 후 ticker로 머지(공유 종목은 한 줄).
     * 합은 정확히 floorAsset+surplusSafe(=safeTarget)이며, 비중은 안전버킷 내 비중(합=1.0).
     */
    private List<Holding> buildSafeHoldings(BigDecimal floorAsset, BigDecimal surplusSafe,
                                            Map<String, EtfInfo> byTicker) {
        BigDecimal safeTotal = floorAsset.add(surplusSafe);
        if (safeTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        Map<String, BigDecimal> amountByTicker = new LinkedHashMap<>();
        accumulate(amountByTicker, PortfolioConstants.SAFE_FLOOR_COMPOSITION, floorAsset);
        accumulate(amountByTicker, PortfolioConstants.SAFE_SURPLUS_COMPOSITION, surplusSafe);
        return toHoldings(amountByTicker, safeTotal, BucketRole.SAFE, byTicker);
    }

    /** 단기버킷 개별 종목 — 원금변동 없는 현금성(CD금리MMF) 100%. 유동성안만 > 0. */
    private List<Holding> buildShortTermHoldings(BigDecimal shortTermBucket, Map<String, EtfInfo> byTicker) {
        if (shortTermBucket.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        Map<String, BigDecimal> amountByTicker = new LinkedHashMap<>();
        accumulate(amountByTicker, PortfolioConstants.SHORT_TERM_COMPOSITION, shortTermBucket);
        return toHoldings(amountByTicker, shortTermBucket, BucketRole.SHORT_TERM, byTicker);
    }

    /** 슬롯 구성비 × 기준금액을 ticker별 금액에 누적(공유 ticker는 합산). */
    private void accumulate(Map<String, BigDecimal> acc, List<SafeSlotWeight> composition, BigDecimal base) {
        for (SafeSlotWeight sw : composition) {
            String ticker = PortfolioConstants.resolveSafeTicker(sw.slot());
            acc.merge(ticker, base.multiply(sw.weight()), BigDecimal::add);
        }
    }

    /** ticker별 금액 → Holding 리스트. 비중 = 금액/버킷합, 풀에 종목 없으면 fail-fast. */
    private List<Holding> toHoldings(Map<String, BigDecimal> amountByTicker, BigDecimal bucketTotal,
                                     BucketRole role, Map<String, EtfInfo> byTicker) {
        List<Holding> holdings = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : amountByTicker.entrySet()) {
            EtfInfo etf = byTicker.get(entry.getKey());
            if (etf == null) {
                throw new BaseException(ErrorCode.INVALID_INPUT); // 풀에 안전/단기 필수 종목 누락
            }
            BigDecimal weight = entry.getValue().divide(bucketTotal, RATE_SCALE, RoundingMode.HALF_UP);
            holdings.add(Holding.of(
                    etf.productId(),
                    etf.ticker(),
                    etf.productName(),
                    role,
                    etf.currency(),
                    weight,
                    money(entry.getValue())
            ));
        }
        return holdings;
    }

    /**
     * 위험버킷 자본차익 과세분 가중 = 과세(해외주식형) 자본차익 / 위험버킷 자본차익 합. 국내주식형은 매매차익 비과세라 제외.
     *
     * <p><b>금액가중이 아니라 자본차익가중</b>이다. 종목 총수익률 r은 동일가정(EXPECTED_TOTAL_RETURN)이나 배당률은
     * 종목마다 달라, 1원당 자본차익(≈ r − 배당률ᵢ)도 다르다. 금액가중(Σ해외금액/Σ금액)은 고배당 해외종목의
     * 과세분을 과대평가한다. 종목별 자본차익액 {@code 금액ᵢ × max(r − 배당률ᵢ, 0)}으로 가중해 과세분을 정밀화한다.
     * 분모(Σ 자본차익액)는 다운스트림 {@code AlphaCoverageCalculator.netRiskAnnuity}의 capGainShare와 정합한다
     * (Σ금액ᵢ(r−배당률ᵢ) = 총액×(r−블렌디드배당); 0클램프만 보수적 차이).
     *
     * <p>자본차익 합이 0(전 종목 배당률≥r)이거나 위험버킷이 비면 1.0 폴백 — 다운스트림 capGainShare도 0이라 영향無.
     * 현 SLOT_TICKER는 위험코어를 전부 해외로 해소해 weight=1.0이므로 이 변경은 현재 출력 무변(국내주식형 위험편입 대비).
     *
     * <p>package-private — 혼합 국내/해외 경로는 현 SLOT_TICKER로 {@code calculate()}에서 도달 불가라 단위테스트로 직접 가드한다.
     */
    BigDecimal weightedCapGainTaxableWeight(List<Holding> holdings, Map<String, EtfInfo> byTicker) {
        BigDecimal totalCapGain = BigDecimal.ZERO;
        BigDecimal taxableCapGain = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal capGain = h.amount().multiply(capGainRate(byTicker.get(h.ticker())));
            totalCapGain = totalCapGain.add(capGain);
            if (!PortfolioConstants.isCapitalGainExempt(h.ticker())) {
                taxableCapGain = taxableCapGain.add(capGain);
            }
        }
        if (totalCapGain.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return taxableCapGain.divide(totalCapGain, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 종목 자본차익률(분수) = max(기대총수익 − 배당률, 0). 배당률(%, 예 3.50)을 분수로 변환. null 배당률은 0(전액 자본차익). */
    private BigDecimal capGainRate(EtfInfo etf) {
        BigDecimal dividendPercent = etf == null ? null : etf.annualDividendRate();
        BigDecimal dividendFrac = dividendPercent == null
                ? BigDecimal.ZERO
                : dividendPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return PortfolioConstants.EXPECTED_TOTAL_RETURN.subtract(dividendFrac).max(BigDecimal.ZERO);
    }

    /** 위험버킷 가중평균 배당률 = Σ(비중 × 배당률). 배당률 null은 0으로. */
    private BigDecimal weightedDividendRate(List<Holding> holdings, Map<String, EtfInfo> byTicker) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal rate = byTicker.get(h.ticker()).annualDividendRate();
            if (rate != null) {
                sum = sum.add(h.weight().multiply(rate));
            }
        }
        return sum.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveShortTermBucket(AllocationInput input) {
        BigDecimal bucket = input.shortTermBucket();
        if (bucket == null || bucket.compareTo(BigDecimal.ZERO) <= 0) {
            return input.surplus().multiply(DEFAULT_SHORT_TERM_RATIO);
        }
        return bucket;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validate(AllocationInput input) {
        if (input == null || input.propensity() == null || input.pool() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        if (input.finalGrade() < 1 || input.finalGrade() > 5) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        requireNonNegative(input.surplus());
        requireNonNegative(input.floorAsset());
        requireNonNegative(input.totalAsset());
        requireNonNegative(input.pensionSaving());
    }

    private void requireNonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }
}
