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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRecommendationMapperTest {

    private final PortfolioRecommendationMapper mapper = new PortfolioRecommendationMapper();

    // 화면 비교용 before 현금흐름 — 이 테스트는 안 추천/배분 매핑만 검증하므로 고정값 사용
    private static final BigDecimal CURRENT_CASH_FLOW = won(2_000_000);
    private static final BigDecimal TARGET_LIVING_COST = won(3_000_000);
    // 보유 차감·매수상한은 이 테스트의 관심사 아님 — 차감 없음(빈 맵)·상한 충분히 크게 둬 holdings 매핑을 그대로 통과시킨다.
    private static final BigDecimal NO_BROKERAGE_BALANCE = won(0);
    private static final BigDecimal UNCONSTRAINED_BUY = won(100_000_000);

    @Test
    void 최고충족률_안이_추천되고_동점이면_리스트순서와_무관하게_STABLE이_우선된다() {
        // BALANCED를 리스트 앞에 두고 충족률을 동일(90)로 줘도 STABLE이 추천돼야 한다
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(
                plan(PlanType.BALANCED), plan(PlanType.STABLE)));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.BALANCED, new BigDecimal("90.00")),
                planCoverage(PlanType.STABLE, new BigDecimal("90.00"))));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW, TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of());

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

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW, TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of());

        assertThat(response.getPlans()).extracting(PlanResponse::getStatus)
                .containsOnly(PlanStatus.AVAILABLE);
    }

    @Test
    void 구조적부족_트랙은_plans가_빈다() {
        AllocationResult allocation = allocation(RecommendationTrack.STRUCTURAL_SHORTAGE, List.of());
        CoverageResult coverage = coverage(RecommendationTrack.STRUCTURAL_SHORTAGE, null, List.of());

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW, TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of());

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

        List<AllocationView> views = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW, TARGET_LIVING_COST,
                        Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of())
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

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW, TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of());

        assertThat(response.getPlans().get(0).getDisplayName()).isEqualTo("안정 월급형");
    }

    @Test
    void 순매수액이_1주_값보다_작은_종목은_매수목록에서_제외된다() {
        // ETF-A: 순매수 300만 ≥ 1주 5만 → 유지 / ETF-B: 순매수 3만 < 1주 50,130 → 0주라 제외(#186)
        Holding buyable = Holding.of(1L, "433330", "ETF-A", BucketRole.RISK,
                CurrencyExposure.UNHEDGED, new BigDecimal("1.0"), won(3_000_000));
        Holding tooSmall = Holding.of(2L, "497880", "CD금리MMF", BucketRole.SHORT_TERM,
                CurrencyExposure.UNHEDGED, new BigDecimal("1.0"), won(30_000));
        PlanAllocation plan = PlanAllocation.builder()
                .type(PlanType.LIQUIDITY)
                .riskTarget(won(3_000_000))
                .safeTarget(won(0))
                .surplusRiskAmount(won(3_000_000))
                .surplusSafeAmount(won(0))
                .shortTermBucket(won(30_000))
                .planDividendRate(new BigDecimal("3.00"))
                .holdings(List.of(buyable, tooSmall))
                .build();
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(plan));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.LIQUIDITY, new BigDecimal("80.00"))));
        Map<Long, BigDecimal> prices = Map.of(1L, won(50_000), 2L, won(50_130));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW,
                TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, prices);

        assertThat(response.getPlans().get(0).getHoldings())
                .extracting(Holding::productId)
                .containsExactly(1L); // ETF-B(2L)는 0주라 제외
    }

    @Test
    void 종목별_월기여는_버킷net운용수입을_버킷내_비중으로_분배하고_단기는_0이다() {
        // SAFE net 10만을 0.6/0.4로, RISK net 5만을 0.7/0.3로 분배. SHORT_TERM(목돈)은 0.
        List<Holding> holdings = List.of(
                Holding.of(10L, "SAFE1", "안전A", BucketRole.SAFE, CurrencyExposure.UNHEDGED, new BigDecimal("0.60"), won(6_000_000)),
                Holding.of(11L, "SAFE2", "안전B", BucketRole.SAFE, CurrencyExposure.UNHEDGED, new BigDecimal("0.40"), won(4_000_000)),
                Holding.of(20L, "RISK1", "위험A", BucketRole.RISK, CurrencyExposure.UNHEDGED, new BigDecimal("0.70"), won(7_000_000)),
                Holding.of(21L, "RISK2", "위험B", BucketRole.RISK, CurrencyExposure.UNHEDGED, new BigDecimal("0.30"), won(3_000_000)),
                Holding.of(30L, "CASH", "단기", BucketRole.SHORT_TERM, CurrencyExposure.UNHEDGED, new BigDecimal("1.00"), won(1_000_000)));
        PlanAllocation plan = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .riskTarget(won(10_000_000))
                .safeTarget(won(10_000_000))
                .surplusRiskAmount(won(10_000_000))
                .surplusSafeAmount(won(10_000_000))
                .shortTermBucket(won(1_000_000))
                .planDividendRate(new BigDecimal("3.00"))
                .holdings(holdings)
                .build();
        AllocationResult allocation = allocation(RecommendationTrack.NORMAL, List.of(plan));
        CoverageResult coverage = coverage(RecommendationTrack.NORMAL, GuidanceBand.TRADEOFF, List.of(
                planCoverage(PlanType.STABLE, new BigDecimal("80.00"), won(100_000), won(50_000))));

        RecommendationResponse response = mapper.toResponse(allocation, coverage, CURRENT_CASH_FLOW,
                TARGET_LIVING_COST, Map.of(), NO_BROKERAGE_BALANCE, UNCONSTRAINED_BUY, Map.of());

        List<Holding> result = response.getPlans().get(0).getHoldings();
        assertThat(result).extracting(Holding::monthlyContribution).doesNotContainNull();

        BigDecimal safeSum = bucketContribution(result, BucketRole.SAFE);
        BigDecimal riskSum = bucketContribution(result, BucketRole.RISK);
        BigDecimal shortSum = bucketContribution(result, BucketRole.SHORT_TERM);
        // Σ = 버킷 net 정합, 단기 0
        assertThat(safeSum).isEqualByComparingTo(won(100_000));
        assertThat(riskSum).isEqualByComparingTo(won(50_000));
        assertThat(shortSum).isEqualByComparingTo("0");
        // 비중 분배 확인 (마지막 종목 잔여흡수)
        assertThat(contributionOf(result, 10L)).isEqualByComparingTo("60000");
        assertThat(contributionOf(result, 11L)).isEqualByComparingTo("40000");
        assertThat(contributionOf(result, 20L)).isEqualByComparingTo("35000");
        assertThat(contributionOf(result, 21L)).isEqualByComparingTo("15000");
    }

    private BigDecimal bucketContribution(List<Holding> holdings, BucketRole role) {
        return holdings.stream()
                .filter(h -> h.role() == role)
                .map(Holding::monthlyContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal contributionOf(List<Holding> holdings, long productId) {
        return holdings.stream()
                .filter(h -> h.productId() == productId)
                .findFirst().orElseThrow()
                .monthlyContribution();
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

    private PlanCoverage planCoverage(PlanType type, BigDecimal rate, BigDecimal safeNet, BigDecimal riskNet) {
        return PlanCoverage.builder()
                .type(type)
                .monthlyIncome(won(2_000_000))
                .alphaCoverageRate(rate)
                .inheritanceAmount(won(0))
                .safeNetIncome(safeNet)
                .riskNetIncome(riskNet)
                .build();
    }

    private Holding holding(String name, long amount) {
        return holding(name, BucketRole.RISK, amount);
    }

    private Holding holding(String name, BucketRole role, long amount) {
        return Holding.of(null, "000000", name, role, CurrencyExposure.UNHEDGED,
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
