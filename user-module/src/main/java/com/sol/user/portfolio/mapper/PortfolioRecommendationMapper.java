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
import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    private static final String SAFE_BUCKET_LABEL = "채권·안전자산";
    private static final String SHORT_TERM_BUCKET_LABEL = "단기 유동성";
    private static final int RATIO_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public RecommendationResponse toResponse(AllocationResult allocation, CoverageResult coverage) {
        Map<PlanType, PlanCoverage> coverageByType = coverage.getPlanCoverages().stream()
                .collect(Collectors.toMap(PlanCoverage::getType, Function.identity()));

        // 추천 안: NORMAL에서 최고 α충족률 1개(동점 시 STABLE 우선). 충족률 null(연금초과)이면 추천 없음.
        PlanType recommendedType = resolveRecommendedType(allocation.getPlans(), coverageByType);

        List<PlanResponse> plans = allocation.getPlans().stream()
                .map(plan -> toPlanResponse(plan, coverageByType, recommendedType))
                .toList();

        String q3Label = coverage.getQ3Scenarios().isEmpty() ? null : Q3_REFERENCE_LABEL;

        return RecommendationResponse.builder()
                .track(coverage.getTrack())   // STEP6가 확정한 최종 트랙
                .alpha(coverage.getAlpha())
                .band(coverage.getBand())
                .plans(plans)
                .q3ReferenceLabel(q3Label)
                .q3Scenarios(coverage.getQ3Scenarios())
                .build();
    }

    private PlanType resolveRecommendedType(List<PlanAllocation> plans,
                                            Map<PlanType, PlanCoverage> coverageByType) {
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
                                        PlanType recommendedType) {
        PlanCoverage coverage = coverageByType.get(plan.getType());
        if (coverage == null) {
            // 배분안과 커버리지는 1:1 매핑 — 누락은 내부 불변식 위반
            throw new IllegalStateException("안별 커버리지 누락: " + plan.getType());
        }

        PlanStatus status = plan.getType() == recommendedType
                ? PlanStatus.RECOMMENDED
                : PlanStatus.AVAILABLE;

        return PlanResponse.builder()
                .type(plan.getType())
                .label(plan.getType().getLabel())
                .displayName(resolveDisplayName(plan.getType()))
                .description(plan.getType().getDescription())
                .status(status)
                .riskTarget(plan.getRiskTarget())
                .safeTarget(plan.getSafeTarget())
                .shortTermBucket(plan.getShortTermBucket())
                .holdings(plan.getHoldings())
                .allocations(buildAllocations(plan))
                .monthlyIncome(coverage.getMonthlyIncome())
                .alphaCoverageRate(coverage.getAlphaCoverageRate())
                .inheritanceAmount(coverage.getInheritanceAmount())
                .build();
    }

    /**
     * 화면 배분 항목 병합. holdings(위험버킷 ETF)는 안전버킷을 포함하지 않으므로
     * safeTarget(바닥자산 포함)·shortTermBucket 집계를 함께 한 리스트로 묶는다.
     * 비중 분모 = riskTarget+safeTarget+shortTermBucket (= 운용자산, 연금저축 제외).
     */
    private List<AllocationView> buildAllocations(PlanAllocation plan) {
        BigDecimal total = plan.getRiskTarget()
                .add(plan.getSafeTarget())
                .add(plan.getShortTermBucket());

        List<AllocationView> views = new ArrayList<>();
        addView(views, SAFE_BUCKET_LABEL, AllocationRole.SAFE, plan.getSafeTarget(), total);
        for (Holding holding : plan.getHoldings()) {
            addView(views, holding.productName(), AllocationRole.RISK, holding.amount(), total);
        }
        addView(views, SHORT_TERM_BUCKET_LABEL, AllocationRole.SHORT_TERM, plan.getShortTermBucket(), total);
        return views;
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
}
