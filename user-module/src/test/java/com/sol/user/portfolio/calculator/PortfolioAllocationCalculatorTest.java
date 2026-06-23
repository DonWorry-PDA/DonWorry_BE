package com.sol.user.portfolio.calculator;

import com.sol.user.portfolio.dto.AllocationInput;
import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.Holding;
import com.sol.user.portfolio.dto.PlanAllocation;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import com.sol.common.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioAllocationCalculatorTest {

    private final PortfolioAllocationCalculator calculator = new PortfolioAllocationCalculator();

    // ── 적극투자형: 3안 제공 + 균형안 구성 + (H) 코어 ──────────────────────────────

    @Test
    void 적극투자형_3안_제공되고_균형안은_3종_구성된다() {
        AllocationResult result = calculator.calculate(activeInput(4));

        assertThat(result.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(result.getPlans()).extracting(PlanAllocation::getType)
                .containsExactly(PlanType.STABLE, PlanType.BALANCED, PlanType.LIQUIDITY);

        // 안정안: 환헤지 코어((H), 452360) 100% (위험 holding 1종)
        PlanAllocation stable = plan(result, PlanType.STABLE);
        List<Holding> stableRisk = riskHoldings(stable);
        assertThat(stableRisk).hasSize(1);
        Holding stableCore = stableRisk.get(0);
        assertThat(stableCore.ticker()).isEqualTo("452360");
        assertThat(stableCore.currency()).isEqualTo(CurrencyExposure.HEDGED);
        assertThat(stableCore.weight()).isEqualByComparingTo("1");
        // riskTarget = 여유분 5.5억 × 위험비중[4][STABLE]=0.45
        assertThat(stable.getRiskTarget()).isEqualByComparingTo("247500000.00");

        // 균형안: 446720(0.35) + 452360(0.35) + 476030(0.30) (위험 holding 3종)
        PlanAllocation balanced = plan(result, PlanType.BALANCED);
        assertThat(riskHoldings(balanced)).extracting(Holding::ticker)
                .containsExactly("446720", "452360", "476030");
        // 가중평균 배당률 = 0.35×3.50 + 0.35×3.40 + 0.30×1.20 = 2.7750 (위험 holding만 반영)
        assertThat(balanced.getPlanDividendRate()).isEqualByComparingTo("2.7750");
    }

    // ── 위험중립형: 균형안 미제공(2안) + 환노출 코어로 강등 ─────────────────────────

    @Test
    void 위험중립형은_균형안없이_2안이며_코어가_환노출로_강등된다() {
        AllocationResult result = calculator.calculate(neutralInput(3));

        assertThat(result.getPlans()).extracting(PlanAllocation::getType)
                .containsExactly(PlanType.STABLE, PlanType.LIQUIDITY);

        PlanAllocation stable = plan(result, PlanType.STABLE);
        Holding core = riskHoldings(stable).get(0);
        assertThat(core.ticker()).isEqualTo("446720");                 // (H) 권유불가 → 환노출 강등
        assertThat(core.currency()).isEqualTo(CurrencyExposure.UNHEDGED);
        assertThat(stable.getPlanDividendRate()).isEqualByComparingTo("3.5000");
    }

    // ── 구조적 부족: 여유분<=0 → 트랙 전환, 3안 스킵 ──────────────────────────────

    @Test
    void 여유분이_0이면_구조적부족_트랙이고_plans가_비어있다() {
        AllocationInput input = activeBuilder(4)
                .surplus(BigDecimal.ZERO)
                .build();

        AllocationResult result = calculator.calculate(input);

        assertThat(result.getTrack()).isEqualTo(RecommendationTrack.STRUCTURAL_SHORTAGE);
        assertThat(result.getPlans()).isEmpty();
    }

    // ── 단기버킷: 유동성안만 선확보(기본 여유분×0.10) ─────────────────────────────

    @Test
    void 유동성안은_단기버킷을_여유분의_10퍼센트로_선확보한다() {
        AllocationResult result = calculator.calculate(activeInput(4));

        PlanAllocation liquidity = plan(result, PlanType.LIQUIDITY);
        assertThat(liquidity.getShortTermBucket()).isEqualByComparingTo("55000000.00"); // 5.5억 × 0.10
        // 단기버킷은 CD금리MMF(497880) 1종으로 개별화, 금액 = shortTermBucket
        List<Holding> shortTerm = liquidity.getHoldings().stream()
                .filter(h -> h.role() == BucketRole.SHORT_TERM).toList();
        assertThat(shortTerm).hasSize(1);
        assertThat(shortTerm.get(0).ticker()).isEqualTo("497880");
        assertThat(shortTerm.get(0).amount()).isEqualByComparingTo("55000000.00");
        // 안정·균형안은 단기버킷 0 + SHORT_TERM holding 없음
        PlanAllocation stable = plan(result, PlanType.STABLE);
        assertThat(stable.getShortTermBucket()).isEqualByComparingTo("0");
        assertThat(stable.getHoldings()).noneMatch(h -> h.role() == BucketRole.SHORT_TERM);
    }

    // ── 안전버킷: 바닥/여유안전 2층이 개별 종목으로 분해되고 합이 safeTarget과 같다 ──────

    @Test
    void 안전버킷이_개별종목으로_분해되고_금액합이_safeTarget과_같으며_비중합이_1이다() {
        AllocationResult result = calculator.calculate(activeInput(4));
        PlanAllocation stable = plan(result, PlanType.STABLE);

        List<Holding> safe = safeHoldings(stable);
        // 바닥(GOV,CASH_EQ) + 여유안전(CREDIT,GOV,CASH_EQ) → ticker 머지 → 국고채·CD금리MMF·종합채권 3종
        assertThat(safe).extracting(Holding::ticker)
                .containsExactlyInAnyOrder("438560", "497880", "436140");

        BigDecimal amountSum = safe.stream().map(Holding::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(amountSum).isEqualByComparingTo(stable.getSafeTarget());

        BigDecimal weightSum = safe.stream().map(Holding::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(weightSum).isEqualByComparingTo("1");
    }

    @Test
    void 안전버킷_바닥자산은_국고채70_예금30_비중으로_담긴다() {
        // 위험비중 0 등급으로는 못 만들지만, 바닥분만 검증하려면 바닥자산 기여분을 분리 계산.
        // 여기선 머지 후 금액으로 바닥+여유안전 합산 비중을 직접 검증한다(바닥5천+여유안전).
        AllocationResult result = calculator.calculate(activeInput(4));
        PlanAllocation stable = plan(result, PlanType.STABLE);
        // floorAsset=5천, surplusSafe=safeTarget−5천. 국고채 금액 = 5천×0.70 + 여유안전×0.30
        BigDecimal surplusSafe = stable.getSafeTarget().subtract(new BigDecimal("50000000"));
        BigDecimal expectedGov = new BigDecimal("50000000").multiply(new BigDecimal("0.70"))
                .add(surplusSafe.multiply(new BigDecimal("0.30")));
        Holding gov = safeHoldings(stable).stream()
                .filter(h -> h.ticker().equals("438560")).findFirst().orElseThrow();
        assertThat(gov.amount()).isEqualByComparingTo(expectedGov.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    // ── 바닥보호 불변식: 모든 안에서 안전목표 >= 바닥자산 ──────────────────────────

    @Test
    void 모든_안에서_안전목표는_바닥자산_이상이다() {
        AllocationResult result = calculator.calculate(activeInput(5)); // 최고 위험비중 등급

        assertThat(result.getPlans()).allSatisfy(p ->
                assertThat(p.getSafeTarget()).isGreaterThanOrEqualTo(new BigDecimal("50000000")));
    }

    // ── 입력 검증: 여유분>0인데 바닥자산>가용자산이면 일관성 위반 ──────────────────

    @Test
    void 여유분이_양수인데_바닥자산이_가용자산을_초과하면_INVALID_INPUT_예외() {
        // 가용자산 = 총자산1억 − 연금저축6천 = 4천 < 바닥자산5천, 그러나 여유분은 양수로 전달(불일치)
        AllocationInput input = activeBuilder(4)
                .surplus(BigDecimal.valueOf(100_000_000))
                .totalAsset(BigDecimal.valueOf(100_000_000))
                .pensionSaving(BigDecimal.valueOf(60_000_000))
                .floorAsset(BigDecimal.valueOf(50_000_000))
                .build();

        assertThatThrownBy(() -> calculator.calculate(input))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 등급_범위를_벗어나면_INVALID_INPUT_예외() {
        AllocationInput input = activeBuilder(6).build(); // 유효 등급 1~5

        assertThatThrownBy(() -> calculator.calculate(input))
                .isInstanceOf(BaseException.class);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private AllocationInput activeInput(int grade) {
        return activeBuilder(grade).build();
    }

    private AllocationInput.AllocationInputBuilder activeBuilder(int grade) {
        return baseBuilder(grade).propensity(InvestmentPropensity.ACTIVE);
    }

    private AllocationInput neutralInput(int grade) {
        return baseBuilder(grade).propensity(InvestmentPropensity.NEUTRAL).build();
    }

    private AllocationInput.AllocationInputBuilder baseBuilder(int grade) {
        return AllocationInput.builder()
                .finalGrade(grade)
                .surplus(BigDecimal.valueOf(550_000_000)) // 5.5억
                .floorAsset(BigDecimal.valueOf(50_000_000))
                .totalAsset(BigDecimal.valueOf(600_000_000))
                .pensionSaving(BigDecimal.ZERO)
                .shortTermBucket(null)
                .pool(pool());
    }

    private List<EtfInfo> pool() {
        return List.of(
                new EtfInfo("446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo("452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo("476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                // 안전버킷 구성 종목(등급≥5)
                new EtfInfo("438560", "SOL 국고채3년", 5,
                        new BigDecimal("3.00"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo("436140", "SOL 종합채권(AA-이상)액티브", 5,
                        new BigDecimal("3.30"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo("497880", "SOL CD금리MMF", 5,
                        new BigDecimal("3.20"), "MONTHLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED)
        );
    }

    private PlanAllocation plan(AllocationResult result, PlanType type) {
        return result.getPlans().stream()
                .filter(p -> p.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private List<Holding> riskHoldings(PlanAllocation plan) {
        return plan.getHoldings().stream().filter(h -> h.role() == BucketRole.RISK).toList();
    }

    private List<Holding> safeHoldings(PlanAllocation plan) {
        return plan.getHoldings().stream().filter(h -> h.role() == BucketRole.SAFE).toList();
    }
}
