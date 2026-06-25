package com.sol.user.portfolio.mapper;

import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.AllocationView;
import com.sol.user.portfolio.dto.CoverageResult;
import com.sol.user.portfolio.dto.Holding;
import com.sol.user.portfolio.dto.PlanAllocation;
import com.sol.user.portfolio.dto.PlanCoverage;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.type.AllocationRole;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * STEP5 배분 + STEP6 수령 결과를 화면용 추천 응답으로 조립한다.
 * 단위(원)·α충족률(%, 100캡)은 도메인 값을 그대로 유지하고, 표시명·status·allocations만 파생한다.
 */
@Component
public class PortfolioRecommendationMapper {

    private static final String Q3_REFERENCE_LABEL = "안정안 기준 예시";
    private static final int RATIO_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public RecommendationResponse toResponse(AllocationResult allocation, CoverageResult coverage,
                                             BigDecimal currentMonthlyCashFlow, BigDecimal targetMonthlyLivingCost,
                                             Map<Long, BigDecimal> existingEvalByProductId,
                                             BigDecimal brokerageBalance, BigDecimal maxBuyTotal) {
        Map<PlanType, PlanCoverage> coverageByType = coverage.getPlanCoverages().stream()
                .collect(Collectors.toMap(PlanCoverage::getType, Function.identity()));

        // 추천 안: NORMAL에서 최고 α충족률 1개(동점 시 STABLE 우선). 연금초과 트랙은 추천 없음.
        PlanType recommendedType = resolveRecommendedType(coverage.getTrack(), allocation.getPlans(), coverageByType);

        List<PlanResponse> plans = allocation.getPlans().stream()
                .map(plan -> toPlanResponse(plan, coverageByType, recommendedType, targetMonthlyLivingCost,
                        existingEvalByProductId, maxBuyTotal))
                .toList();

        String q3Label = coverage.getQ3Scenarios().isEmpty() ? null : Q3_REFERENCE_LABEL;

        BigDecimal currentCoverageRate = coverageRate(currentMonthlyCashFlow, targetMonthlyLivingCost);
        BigDecimal currentShortfall = targetMonthlyLivingCost.subtract(currentMonthlyCashFlow).max(BigDecimal.ZERO)
                .setScale(RATIO_SCALE, RoundingMode.HALF_UP);

        return RecommendationResponse.builder()
                .track(coverage.getTrack())   // STEP6가 확정한 최종 트랙
                .alpha(coverage.getAlpha())
                .band(coverage.getBand())
                .plans(plans)
                .targetMonthlyLivingCost(targetMonthlyLivingCost)
                .currentMonthlyCashFlow(currentMonthlyCashFlow)
                .currentCoverageRate(currentCoverageRate)
                .currentMonthlyShortfall(currentShortfall)
                .q3ReferenceLabel(q3Label)
                .q3Scenarios(coverage.getQ3Scenarios())
                .brokerageBalance(brokerageBalance)
                .build();
    }

    private PlanType resolveRecommendedType(RecommendationTrack track,
                                            List<PlanAllocation> plans,
                                            Map<PlanType, PlanCoverage> coverageByType) {
        // 연금초과(PENSION_SUFFICIENT)는 추천 없이 전부 AVAILABLE.
        // 충족률 null 의존 대신 트랙으로 명시 가드 — 향후 충족률이 채워져도 RECOMMENDED로 오인하지 않도록.
        if (track == RecommendationTrack.PENSION_SUFFICIENT) {
            return null;
        }
        PlanType best = null;
        BigDecimal bestRate = null;
        for (PlanAllocation plan : plans) {
            PlanCoverage coverage = coverageByType.get(plan.getType());
            BigDecimal rate = (coverage == null) ? null : coverage.getAlphaCoverageRate();
            if (rate == null) {
                continue; // 연금초과 등 — 추천 대상 아님
            }
            boolean higher = bestRate == null || rate.compareTo(bestRate) > 0;
            boolean tieAndStable = bestRate != null
                    && rate.compareTo(bestRate) == 0
                    && plan.getType() == PlanType.STABLE;
            if (higher || tieAndStable) {
                best = plan.getType();
                bestRate = rate;
            }
        }
        return best;
    }

    private PlanResponse toPlanResponse(PlanAllocation plan,
                                        Map<PlanType, PlanCoverage> coverageByType,
                                        PlanType recommendedType,
                                        BigDecimal targetMonthlyLivingCost,
                                        Map<Long, BigDecimal> existingEvalByProductId,
                                        BigDecimal maxBuyTotal) {
        PlanCoverage coverage = coverageByType.get(plan.getType());
        if (coverage == null) {
            // 배분안과 커버리지는 1:1 매핑 — 누락은 내부 불변식 위반
            throw new IllegalStateException("안별 커버리지 누락: " + plan.getType());
        }

        PlanStatus status = plan.getType() == recommendedType
                ? PlanStatus.RECOMMENDED
                : PlanStatus.AVAILABLE;

        BigDecimal monthlyIncome = coverage.getMonthlyIncome();
        BigDecimal totalCoverageRate = coverageRate(monthlyIncome, targetMonthlyLivingCost);
        BigDecimal residualShortfall = (targetMonthlyLivingCost == null || monthlyIncome == null)
                ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                : targetMonthlyLivingCost.subtract(monthlyIncome).max(BigDecimal.ZERO)
                        .setScale(RATIO_SCALE, RoundingMode.HALF_UP);

        return PlanResponse.builder()
                .type(plan.getType())
                .label(plan.getType().getLabel())
                .displayName(resolveDisplayName(plan.getType()))
                .description(plan.getType().getDescription())
                .status(status)
                .riskTarget(plan.getRiskTarget())
                .safeTarget(plan.getSafeTarget())
                .shortTermBucket(plan.getShortTermBucket())
                .holdings(netHoldings(plan.getHoldings(), existingEvalByProductId, maxBuyTotal))
                .allocations(buildAllocations(plan))
                .monthlyIncome(monthlyIncome)
                .alphaCoverageRate(coverage.getAlphaCoverageRate())
                .sustainableCoverageRate(coverage.getSustainableCoverageRate())
                .inheritanceAmount(coverage.getInheritanceAmount())
                .totalCoverageRate(totalCoverageRate)
                .residualMonthlyShortfall(residualShortfall)
                .build();
    }

    /**
     * 화면 배분 항목. 비중 분모 = riskTarget+safeTarget+shortTermBucket (= 운용자산, 연금저축 제외).
     */
    private List<AllocationView> buildAllocations(PlanAllocation plan) {
        BigDecimal total = plan.getRiskTarget()
                .add(plan.getSafeTarget())
                .add(plan.getShortTermBucket());

        List<AllocationView> views = new ArrayList<>();
        for (Holding holding : plan.getHoldings()) {
            addView(views, holding.productName(), toAllocationRole(holding.role()), holding.amount(), total);
        }
        return views;
    }

    private AllocationRole toAllocationRole(BucketRole role) {
        return switch (role) {
            case RISK -> AllocationRole.RISK;
            case SAFE -> AllocationRole.SAFE;
            case SHORT_TERM -> AllocationRole.SHORT_TERM;
        };
    }

    private void addView(List<AllocationView> views, String label, AllocationRole role,
                         BigDecimal amount, BigDecimal total) {
        if (amount == null || amount.signum() <= 0) {
            return; // 0원 항목(예: 위험 0인 안의 단기버킷)은 노이즈라 제외
        }
        views.add(new AllocationView(label, role, ratio(amount, total), amount));
    }

    private BigDecimal ratio(BigDecimal amount, BigDecimal total) {
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE);
        }
        return amount.multiply(HUNDRED).divide(total, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private String resolveDisplayName(PlanType type) {
        return switch (type) {
            case STABLE -> "안정 월급형";
            case BALANCED -> "균형 월급형";
            case LIQUIDITY -> "여유자금 성장형";
        };
    }

    /**
     * 추천 종목별 순 매수 금액 = 목표 배분액 − 기존 보유 평가액.
     * 동일 productId가 SAFE/SHORT_TERM 버킷에 중복 등장(예: SOL CD금리MMF)하는 경우
     * remaining을 순차 차감해 이중 공제를 방지한다. net ≤ 0인 항목은 제거.
     */
    /**
     * 추천 종목별 순 매수 금액 = 목표 배분액 − 기존 보유 평가액.
     * 동일 productId가 SAFE/SHORT_TERM 버킷에 중복 등장(예: SOL CD금리MMF)하는 경우
     * remaining을 순차 차감해 이중 공제를 방지한다. net ≤ 0인 항목은 제거.
     *
     * <p>추천 목록에 없는 기존 BROKERAGE ETF(예: 개별 액티브 ETF)는 productId 매칭이 안 되어
     * per-product 차감이 불가능하다. 이를 maxBuyTotal(= 가용현금)로 전체 합계를 상한하여
     * 실제 이체·매수 가능 금액을 초과하지 않도록 한다.
     */
    private List<Holding> netHoldings(List<Holding> holdings, Map<Long, BigDecimal> existingByProductId,
                                      BigDecimal maxBuyTotal) {
        if (maxBuyTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        if (existingByProductId.isEmpty()) {
            return holdings;
        }
        Map<Long, BigDecimal> remaining = new HashMap<>(existingByProductId);
        List<Holding> result = new ArrayList<>();
        for (Holding h : holdings) {
            BigDecimal rem = remaining.getOrDefault(h.productId(), BigDecimal.ZERO);
            BigDecimal net = h.amount().subtract(rem).max(BigDecimal.ZERO)
                    .setScale(RATIO_SCALE, RoundingMode.HALF_UP);
            remaining.put(h.productId(), rem.subtract(h.amount().min(rem)));
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new Holding(h.productId(), h.ticker(), h.productName(),
                        h.role(), h.currency(), h.weight(), net));
            }
        }
        // 추천 목록에 없는 기존 ETF 평가액이 있으면 per-product 차감이 안 된다.
        // 전체 합계가 maxBuyTotal(= 가용 현금)을 초과하면 비율대로 축소한다.
        BigDecimal netTotal = result.stream().map(Holding::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netTotal.compareTo(maxBuyTotal) > 0) {
            BigDecimal scale = maxBuyTotal.divide(netTotal, 10, RoundingMode.HALF_UP);
            List<Holding> scaled = new ArrayList<>();
            for (Holding h : result) {
                BigDecimal s = h.amount().multiply(scale).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
                if (s.compareTo(BigDecimal.ZERO) > 0) {
                    scaled.add(new Holding(h.productId(), h.ticker(), h.productName(),
                            h.role(), h.currency(), h.weight(), s));
                }
            }
            return scaled;
        }
        return result;
    }

    /** 월수령 / 목표생활비 × 100 (%). 어느 한쪽이 null이거나 목표생활비가 0이면 0 반환. */
    private BigDecimal coverageRate(BigDecimal monthlyIncome, BigDecimal targetLivingCost) {
        if (monthlyIncome == null || targetLivingCost == null || targetLivingCost.signum() <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE);
        }
        return monthlyIncome.multiply(HUNDRED).divide(targetLivingCost, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
