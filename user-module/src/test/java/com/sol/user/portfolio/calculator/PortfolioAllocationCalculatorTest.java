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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // ── #118 권유가능등급 필터: 안정형/안정추구형은 위험버킷이 비고 안전으로 흡수된다 ──────

    @Test
    void 안정형은_위험버킷이_비고_위험액이_안전으로_흡수된다() {
        // STABLE(권유최소등급5)은 NEUTRAL tier로 매핑되나, 위험코어 446720(등급3)은 권유 불가 → 위험 0
        AllocationResult result = calculator.calculate(stableInput(3));

        assertThat(result.getPlans()).extracting(PlanAllocation::getType)
                .containsExactly(PlanType.STABLE, PlanType.LIQUIDITY); // NEUTRAL tier 안 구성

        PlanAllocation stable = plan(result, PlanType.STABLE);
        assertThat(riskHoldings(stable)).isEmpty();
        assertThat(stable.getRiskTarget()).isEqualByComparingTo("0");
        assertThat(stable.getPlanDividendRate()).isEqualByComparingTo("0");
        // 위험 0 → 안전목표 = 총자산 − 연금저축 − 단기버킷(안정안은 0)
        assertThat(stable.getSafeTarget()).isEqualByComparingTo("600000000.00");
        // 안전버킷이 모든 금액을 흡수(합 = safeTarget)
        BigDecimal safeSum = safeHoldings(stable).stream()
                .map(Holding::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(safeSum).isEqualByComparingTo(stable.getSafeTarget());
    }

    @Test
    void 안정추구형도_위험등급_초과상품이_배분되지_않는다() {
        // STABLE_SEEKING(권유최소등급4) — 446720(등급3) < 4 → 권유 불가
        AllocationResult result = calculator.calculate(stableSeekingInput(3));

        assertThat(result.getPlans()).allSatisfy(p ->
                assertThat(riskHoldings(p)).isEmpty());
    }

    @Test
    void 위험중립형은_권유가능_위험상품을_여전히_배분받는다() {
        // 회귀: NEUTRAL(권유최소등급3) — 446720(등급3) >= 3 → 권유 가능, 필터는 no-op
        AllocationResult result = calculator.calculate(neutralInput(3));

        PlanAllocation stable = plan(result, PlanType.STABLE);
        assertThat(riskHoldings(stable)).extracting(Holding::ticker).containsExactly("446720");
        assertThat(stable.getRiskTarget()).isGreaterThan(BigDecimal.ZERO);
    }

    // ── #192 위험버킷 자본차익 과세분 가중 산출 ───────────────────────────────────

    @Test
    void 위험버킷이_전부_해외주식형이면_자본차익_과세분가중은_1이다() {
        // 현 SLOT_TICKER는 위험코어를 전부 해외(446720·452360·476030)로 해소 → 전액 과세
        AllocationResult result = calculator.calculate(activeInput(4));

        PlanAllocation balanced = plan(result, PlanType.BALANCED); // 446720·452360·476030 3종
        assertThat(balanced.getRiskCapGainTaxableWeight()).isEqualByComparingTo("1");
        PlanAllocation stable = plan(result, PlanType.STABLE);     // 452360 단일
        assertThat(stable.getRiskCapGainTaxableWeight()).isEqualByComparingTo("1");
    }

    @Test
    void 위험버킷이_비면_자본차익_과세분가중은_1로_폴백된다() {
        // 안정형: 위험버킷 비고 안전 흡수 → 분모 0 → 1.0 폴백(전액 과세, 영향 없음)
        AllocationResult result = calculator.calculate(stableInput(3));

        PlanAllocation stable = plan(result, PlanType.STABLE);
        assertThat(riskHoldings(stable)).isEmpty();
        assertThat(stable.getRiskCapGainTaxableWeight()).isEqualByComparingTo("1");
    }

    // ── #193 혼합 국내/해외: 금액가중이 아니라 자본차익가중(과대평가 방지) ────────────────
    //    현 SLOT_TICKER로는 calculate()에서 국내주식형이 위험버킷에 들어오지 않으므로 헬퍼를 직접 검증.

    @Test
    void 혼합포트는_금액가중보다_낮은_자본차익가중_과세분율을_낸다() {
        // 해외 446720(배당3.50%, 자본차익률 max(4.5−3.5)=1.0%) 5천 + 국내 292500(배당1.50%, 자본차익 3.0%) 5천
        // 금액가중이면 해외 5천/1억 = 0.5. 자본차익가중 = (5천×0.01) / (5천×0.01 + 5천×0.03) = 50/200 = 0.25.
        Map<String, EtfInfo> byTicker = byTicker(
                new EtfInfo(1L, "446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(2L, "292500", "SOL KRX300", 2,
                        new BigDecimal("1.50"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED));
        List<Holding> holdings = List.of(
                risk("446720", byTicker, 50_000_000),
                risk("292500", byTicker, 50_000_000));

        BigDecimal weight = calculator.weightedCapGainTaxableWeight(holdings, byTicker);

        assertThat(weight).isEqualByComparingTo("0.2500");
    }

    @Test
    void 전부_해외면_자본차익가중도_1로_금액가중과_동일하다() {
        // 회귀: 모두 과세(해외)면 자본차익가중도 numerator=denominator → 1.0
        Map<String, EtfInfo> byTicker = byTicker(
                new EtfInfo(1L, "446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(3L, "476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED));
        List<Holding> holdings = List.of(
                risk("446720", byTicker, 40_000_000),
                risk("476030", byTicker, 60_000_000));

        assertThat(calculator.weightedCapGainTaxableWeight(holdings, byTicker)).isEqualByComparingTo("1");
    }

    @Test
    void 배당률이_기대총수익_이상이면_자본차익_0이라_폴백1() {
        // 전 종목 배당률 ≥ 4.5% → max(r−배당,0)=0 → 분모 0 → 1.0 폴백(다운스트림 capGainShare도 0)
        Map<String, EtfInfo> byTicker = byTicker(
                new EtfInfo(2L, "292500", "SOL KRX300", 2,
                        new BigDecimal("5.00"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED));
        List<Holding> holdings = List.of(risk("292500", byTicker, 50_000_000));

        assertThat(calculator.weightedCapGainTaxableWeight(holdings, byTicker)).isEqualByComparingTo("1");
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

    // ── productId 전파: EtfInfo.productId()가 Holding.productId()로 그대로 내려온다 ──

    @Test
    void EtfInfo의_productId가_위험_및_안전_Holding에_전파된다() {
        AllocationResult result = calculator.calculate(activeInput(4));
        PlanAllocation stable = plan(result, PlanType.STABLE);

        // 위험버킷: 452360 → productId=2
        Holding riskCore = riskHoldings(stable).get(0);
        assertThat(riskCore.ticker()).isEqualTo("452360");
        assertThat(riskCore.productId()).isEqualTo(2L);

        // 안전버킷: 438560(국고채) → productId=4
        Holding gov = safeHoldings(stable).stream()
                .filter(h -> h.ticker().equals("438560")).findFirst().orElseThrow();
        assertThat(gov.productId()).isEqualTo(4L);
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

    private AllocationInput stableInput(int grade) {
        return baseBuilder(grade).propensity(InvestmentPropensity.STABLE).build();
    }

    private AllocationInput stableSeekingInput(int grade) {
        return baseBuilder(grade).propensity(InvestmentPropensity.STABLE_SEEKING).build();
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
                new EtfInfo(1L, "446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(2L, "452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo(3L, "476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                // 안전버킷 구성 종목(등급≥5)
                new EtfInfo(4L, "438560", "SOL 국고채3년", 5,
                        new BigDecimal("3.00"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo(5L, "436140", "SOL 종합채권(AA-이상)액티브", 5,
                        new BigDecimal("3.30"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo(6L, "497880", "SOL CD금리MMF", 5,
                        new BigDecimal("3.20"), "MONTHLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED)
        );
    }

    private Map<String, EtfInfo> byTicker(EtfInfo... etfs) {
        return List.of(etfs).stream().collect(Collectors.toMap(EtfInfo::ticker, Function.identity()));
    }

    /** 위험버킷 holding — weightedCapGainTaxableWeight는 amount·ticker만 보므로 나머지는 byTicker에서 채운다. */
    private Holding risk(String ticker, Map<String, EtfInfo> byTicker, long amount) {
        EtfInfo etf = byTicker.get(ticker);
        return Holding.of(etf.productId(), ticker, etf.productName(), BucketRole.RISK,
                etf.currency(), BigDecimal.ZERO, BigDecimal.valueOf(amount));
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
