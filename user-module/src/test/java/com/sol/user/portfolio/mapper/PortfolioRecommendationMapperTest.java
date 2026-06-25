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
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.GuidanceBand;
import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRecommendationMapperTest {

    private final PortfolioRecommendationMapper mapper = new PortfolioRecommendationMapper();

    @Test
    void 최고충족률_안이_추천되고_동점이면_리스트순서와_무관하게_STABLE이_우선된다() {
        // BALANCED를 리스트 앞에 두고 충족률을 동일(90)로 줘도 STABLE이 추천돼야 한다
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(
                plan(PlanType.BALANCED), plan(PlanType.STABLE)));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.BALANCED, new BigDecimal("90.00")),
                planCoverage(PlanType.STABLE, new BigDecimal("90.00"))));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(statusOf(response, PlanType.STABLE)).isEqualTo(PlanStatus.RECOMMENDED);
        assertThat(statusOf(response, PlanType.BALANCED)).isEqualTo(PlanStatus.AVAILABLE);
        assertThat(response.getPlans())
                .filteredOn(p -> p.getStatus() == PlanStatus.RECOMMENDED).hasSize(1);
    }

    @Test
    void 충족률이_null인_연금초과_트랙은_추천안이_없다() {
        AllocationResult allocation = allocation(RecommendationTrack.PENSION_SUFFICIENT, List.of(
                plan(PlanType.STABLE), plan(PlanType.LIQUIDITY)));
        CoverageResult coverage = coverage(RecommendationTrack.PENSION_SUFFICIENT, null, List.of(
                planCoverage(PlanType.STABLE, null),
                planCoverage(PlanType.LIQUIDITY, null)));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(response.getPlans()).extracting(PlanResponse::getStatus)
                .containsOnly(PlanStatus.AVAILABLE);
    }

    @Test
    void 구조적부족_트랙은_plans가_빈다() {
        AllocationResult allocation = allocation(RecommendationTrack.STRUCTURAL_SHORTAGE, List.of());
        CoverageResult coverage = coverage(RecommendationTrack.STRUCTURAL_SHORTAGE, null, List.of());

        RecommendationResponse response = mapper.toResponse(allocation, coverage, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(response.getPlans()).isEmpty();
        assertThat(response.getQ3ReferenceLabel()).isNull();
    }

    @Test
    void allocations는_holding의_role을_그대로_매핑하고_집계없이_비중합이_100이다() {
        // total = safe 5,000,000 + risk 4,000,000 + short 1,000,000 = 10,000,000
        // holdings에 안전·위험·단기 개별 종목이 모두 들어있음(STEP5 분해 결과)
        PlanAllocation plan = PlanAllocation.builder()
                .type(PlanType.LIQUIDITY)
                .riskTarget(won(4_000_000))
                .safeTarget(won(5_000_000))
                .surplusRiskAmount(won(4_000_000))
                .surplusSafeAmount(won(5_000_000))
                .shortTermBucket(won(1_000_000))
                .planDividendRate(new BigDecimal("3.00"))
                .holdings(List.of(
                        holding("국고채", BucketRole.SAFE, 5_000_000),
                        holding("ETF-A", BucketRole.RISK, 3_000_000),
                        holding("ETF-B", BucketRole.RISK, 1_000_000),
                        holding("CD금리MMF", BucketRole.SHORT_TERM, 1_000_000)))
                .build();
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(plan));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.LIQUIDITY, new BigDecimal("80.00"))));

        List<AllocationView> views = mapper.toResponse(allocation, coverage, BigDecimal.ZERO, BigDecimal.ZERO)
                .getPlans().get(0).getAllocations();

        assertThat(views).extracting(AllocationView::role)
                .containsExactly(AllocationRole.SAFE, AllocationRole.RISK, AllocationRole.RISK, AllocationRole.SHORT_TERM);
        assertThat(views).extracting(AllocationView::ratio)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("30.00"),
                        new BigDecimal("10.00"), new BigDecimal("10.00"));
        // 안전버킷이 집계 항목으로 중복 추가되지 않음(이중계상 방지) — 항목 수 = holding 수
        assertThat(views).hasSize(4);
        BigDecimal ratioSum = views.stream().map(AllocationView::ratio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(ratioSum).isEqualByComparingTo("100.00");
    }

    @Test
    void displayName은_안별_표시명으로_매핑된다() {
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(plan(PlanType.STABLE)));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.STABLE, new BigDecimal("80.00"))));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(response.getPlans().get(0).getDisplayName()).isEqualTo("안정 월급형");
    }

    // ── helpers ──

    private AllocationResult allocation(RecommendationTrack track, List<PlanAllocation> plans) {
        return AllocationResult.builder().track(track).plans(plans).build();
    }

    private CoverageResult coverage(RecommendationTrack track, GuidanceBand band, List<PlanCoverage> coverages) {
        return CoverageResult.builder()
                .track(track)
                .alpha(won(1_000_000))
                .band(band)
                .planCoverages(coverages)
                .q3Scenarios(List.of())
                .build();
    }

    private PlanAllocation plan(PlanType type) {
        return PlanAllocation.builder()
                .type(type)
                .riskTarget(won(3_000_000))
                .safeTarget(won(7_000_000))
                .surplusRiskAmount(won(3_000_000))
                .surplusSafeAmount(won(7_000_000))
                .shortTermBucket(won(0))
                .planDividendRate(new BigDecimal("3.00"))
                .holdings(List.of(holding("ETF-A", 3_000_000)))
                .build();
    }

    private PlanCoverage planCoverage(PlanType type, BigDecimal rate) {
        return PlanCoverage.builder()
                .type(type)
                .monthlyIncome(won(2_000_000))
                .alphaCoverageRate(rate)
                .inheritanceAmount(won(0))
                .build();
    }

    private Holding holding(String name, long amount) {
        return holding(name, BucketRole.RISK, amount);
    }

    private Holding holding(String name, BucketRole role, long amount) {
        return new Holding(null, "000000", name, role, CurrencyExposure.UNHEDGED,
                new BigDecimal("1.0"), won(amount));
    }

    private PlanStatus statusOf(RecommendationResponse response, PlanType type) {
        return response.getPlans().stream()
                .filter(p -> p.getType() == type)
                .findFirst().orElseThrow()
                .getStatus();
    }

    private static BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
