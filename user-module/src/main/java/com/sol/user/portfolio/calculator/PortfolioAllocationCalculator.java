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

        BigDecimal safeTarget = input.totalAsset()
                .subtract(riskTarget)
                .subtract(input.pensionSaving())
                .subtract(shortTermBucket);

        // STEP6용 분리값
        BigDecimal surplusRiskAmount = riskTarget;
        BigDecimal surplusSafeAmount = surplus.subtract(riskTarget).subtract(shortTermBucket).max(BigDecimal.ZERO);

        // 위험버킷 — 배당률은 위험 holding만으로 가중평균(STEP6 입력, 안전·단기 섞이면 오염되므로 먼저 계산)
        List<Holding> riskHoldings = buildRiskHoldings(plan, tier, riskTarget, byTicker);
        BigDecimal planDividendRate = weightedDividendRate(riskHoldings, byTicker);

        // 안전버킷 — 바닥(원금보존)/여유안전(소진) 2층. 합이 정확히 safeTarget이 되도록 여유안전분=safeTarget−바닥자산.
        // (바닥보호 불변식상 safeTarget >= floorAsset 보장, 음수 방지 위해 clamp)
        BigDecimal floorAsset = input.floorAsset();
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
                .holdings(holdings)
                .build();
    }

    private List<Holding> buildRiskHoldings(PlanType plan, PropensityTier tier,
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
            holdings.add(new Holding(
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
            holdings.add(new Holding(
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
