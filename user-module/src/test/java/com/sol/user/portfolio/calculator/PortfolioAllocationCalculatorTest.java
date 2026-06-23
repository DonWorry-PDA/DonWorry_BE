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

        // 안정안: 환헤지 코어((H), 452360) 100%
        PlanAllocation stable = plan(result, PlanType.STABLE);
        assertThat(stable.getHoldings()).hasSize(1);
        Holding stableCore = stable.getHoldings().get(0);
        assertThat(stableCore.ticker()).isEqualTo("452360");
        assertThat(stableCore.currency()).isEqualTo(CurrencyExposure.HEDGED);
        assertThat(stableCore.weight()).isEqualByComparingTo("1");
        // riskTarget = 여유분 5.5억 × 위험비중[4][STABLE]=0.45
        assertThat(stable.getRiskTarget()).isEqualByComparingTo("247500000.00");

        // 균형안: 446720(0.35) + 452360(0.35) + 476030(0.30)
        PlanAllocation balanced = plan(result, PlanType.BALANCED);
        assertThat(balanced.getHoldings()).extracting(Holding::ticker)
                .containsExactly("446720", "452360", "476030");
        // 가중평균 배당률 = 0.35×3.50 + 0.35×3.40 + 0.30×1.20 = 2.7750
        assertThat(balanced.getPlanDividendRate()).isEqualByComparingTo("2.7750");
    }

    // ── 위험중립형: 균형안 미제공(2안) + 환노출 코어로 강등 ─────────────────────────

    @Test
    void 위험중립형은_균형안없이_2안이며_코어가_환노출로_강등된다() {
        AllocationResult result = calculator.calculate(neutralInput(3));

        assertThat(result.getPlans()).extracting(PlanAllocation::getType)
                .containsExactly(PlanType.STABLE, PlanType.LIQUIDITY);

        PlanAllocation stable = plan(result, PlanType.STABLE);
        Holding core = stable.getHoldings().get(0);
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
        // 안정·균형안은 단기버킷 0
        assertThat(plan(result, PlanType.STABLE).getShortTermBucket()).isEqualByComparingTo("0");
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
                new EtfInfo(null, "446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(null, "452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo(null, "476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED)
        );
    }

    private PlanAllocation plan(AllocationResult result, PlanType type) {
        return result.getPlans().stream()
                .filter(p -> p.getType() == type)
                .findFirst()
                .orElseThrow();
    }
}
