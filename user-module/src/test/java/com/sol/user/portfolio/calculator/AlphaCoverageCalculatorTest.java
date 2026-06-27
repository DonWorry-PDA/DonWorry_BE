package com.sol.user.portfolio.calculator;

import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.CoverageInput;
import com.sol.user.portfolio.dto.CoverageResult;
import com.sol.user.portfolio.dto.PlanAllocation;
import com.sol.user.portfolio.dto.PlanCoverage;
import com.sol.user.portfolio.dto.Q3Scenario;
import com.sol.user.portfolio.type.GuidanceBand;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaCoverageCalculatorTest {

    private final AlphaCoverageCalculator calculator = new AlphaCoverageCalculator();

    // ── NORMAL: α 충족률 + 100% 캡 + band ──────────────────────────────────────

    @Test
    void 정상_운용분이_알파를_넘으면_충족률은_100퍼센트로_캡되고_band는_SUFFICIENT() {
        // 여유분을 크게 잡아 운용 월수령이 α(200만)를 확실히 초과 → 충족률 100% 캡 검증
        // (PMT 소진모델에선 기본 여유분으론 α 미달이라, 캡 경로를 타도록 여유분을 키운다)
        PlanAllocation rich = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.valueOf(200_000_000))
                .surplusSafeAmount(BigDecimal.valueOf(400_000_000))
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(new BigDecimal("3.0000"))
                .holdings(List.of())
                .build();
        CoverageResult result = calculator.calculate(baseBuilder()
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL)
                        .plans(List.of(rich))
                        .build())
                .build());

        assertThat(result.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(result.getAlpha()).isEqualByComparingTo("2000000"); // 300만−100만−0
        PlanCoverage stable = result.getPlanCoverages().get(0);
        assertThat(stable.getAlphaCoverageRate()).isEqualByComparingTo("100"); // 캡
        assertThat(result.getBand()).isEqualTo(GuidanceBand.SUFFICIENT);
        assertThat(result.getQ3Scenarios()).hasSize(3);
    }

    // ── α≤0: 연금초과 트랙, 충족률 미적용(null) ───────────────────────────────────

    @Test
    void 기타수입으로_알파가_0이하면_연금초과_트랙이고_충족률은_null이다() {
        CoverageInput input = baseBuilder()
                .otherRegularIncome(BigDecimal.valueOf(2_500_000)) // 300만−100만−250만 = −50만
                .build();

        CoverageResult result = calculator.calculate(input);

        assertThat(result.getTrack()).isEqualTo(RecommendationTrack.PENSION_SUFFICIENT);
        assertThat(result.getBand()).isNull();
        assertThat(result.getPlanCoverages()).isNotEmpty();
        assertThat(result.getPlanCoverages()).allSatisfy(
                pc -> assertThat(pc.getAlphaCoverageRate()).isNull());
    }

    // ── 구조적 부족 입력은 그대로 전파(coverage 없음) ──────────────────────────────

    @Test
    void 구조적부족_입력이면_트랙_전파되고_coverage와_q3표가_비어있다() {
        CoverageInput input = baseBuilder()
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.STRUCTURAL_SHORTAGE)
                        .plans(List.of())
                        .build())
                .build();

        CoverageResult result = calculator.calculate(input);

        assertThat(result.getTrack()).isEqualTo(RecommendationTrack.STRUCTURAL_SHORTAGE);
        assertThat(result.getPlanCoverages()).isEmpty();
        assertThat(result.getQ3Scenarios()).isEmpty();
    }

    // ── age<55: 연금저축 인출 불가 → 전액 상속(소비우선이라도) ─────────────────────

    @Test
    void age_55미만이면_소비우선이라도_연금저축은_인출되지않고_전액_상속된다() {
        CoverageInput input = baseBuilder()
                .age(50)
                .q3(2) // 소비우선 → 여유분 전액 소진(상속 0이 되어야 정상)
                .build();

        PlanCoverage stable = calculator.calculate(input).getPlanCoverages().get(0);
        // 여유위험·여유안전은 전액 소진(0), 연금저축 5천만만 상속으로 남음
        assertThat(stable.getInheritanceAmount()).isEqualByComparingTo("50000000");
    }

    // ── Q3 트레이드오프: 소비우선=상속0·월수령최대, 상속우선=상속최대·월수령최소 ──────

    @Test
    void q3_트레이드오프_소비우선일수록_월수령은_늘고_상속은_준다() {
        List<Q3Scenario> scenarios = calculator.calculate(baseBuilder().build()).getQ3Scenarios();

        Q3Scenario inheritFirst = scenario(scenarios, 0); // 상속우선
        Q3Scenario consumeFirst = scenario(scenarios, 2); // 소비우선 (age 65라 연금저축도 소진)

        assertThat(consumeFirst.inheritanceAmount()).isEqualByComparingTo("0");
        assertThat(inheritFirst.inheritanceAmount()).isGreaterThan(consumeFirst.inheritanceAmount());
        assertThat(consumeFirst.monthlyIncome()).isGreaterThan(inheritFirst.monthlyIncome());
    }

    // ── 자산 보존식: Σ원금소진 + 상속분 + 바닥자산 = 총자산 (배당·이자 제외) ──────────

    @Test
    void 원금소진_더하기_상속_더하기_바닥자산은_총자산과_같다() {
        // 안정안(단기버킷0): 총자산 = 여유위험1억 + 여유안전2억 + 연금저축5천 + 바닥5천 = 4억
        CoverageInput input = baseBuilder().q3(1).age(65).build();
        BigDecimal floorAsset = BigDecimal.valueOf(50_000_000);
        BigDecimal totalAsset = BigDecimal.valueOf(400_000_000);
        BigDecimal ratio = new BigDecimal("0.65");
        BigDecimal depletable = BigDecimal.valueOf(350_000_000);   // 여유위험+여유안전+연금저축
        BigDecimal principalDepleted = depletable.multiply(ratio); // Σ원금소진

        PlanCoverage stable = calculator.calculate(input).getPlanCoverages().get(0);

        BigDecimal conserved = stable.getInheritanceAmount().add(principalDepleted).add(floorAsset);
        assertThat(conserved).isEqualByComparingTo(totalAsset);
    }

    @Test
    void 단기버킷이_있으면_보존식은_월수입_단기목돈_상속_바닥의_4항으로_맞아떨어진다() {
        // 유동성안: 단기버킷 5천만은 소진(월수입)도 상속도 아닌 별도 '단기 목돈' 항으로 잡혀야
        //   총자산 = Σ원금소진 + 단기목돈 + 상속 + 바닥 이 한 푼도 안 새고 성립한다.
        // 총자산 = 여유위험1억 + 여유안전2억 + 연금저축5천 + 바닥5천 + 단기5천 = 4.5억
        // type은 STABLE로 둔다 — Q3표가 대표안(STABLE)을 요구하고, compute()는 type 무관하게
        //   surplusRisk/surplusSafe/shortTermBucket만 쓰므로 단기버킷 보존식 검증에 영향 없다.
        BigDecimal shortTermBucket = BigDecimal.valueOf(50_000_000);
        PlanAllocation liquidity = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.valueOf(100_000_000))
                .surplusSafeAmount(BigDecimal.valueOf(200_000_000))
                .shortTermBucket(shortTermBucket)
                .planDividendRate(new BigDecimal("3.0000")) // 실질자본성장 0(상속=보존원금) → 보존식 단순합 성립
                .holdings(List.of())
                .build();
        CoverageInput input = baseBuilder()
                .q3(1)
                .age(65)
                .floorAsset(BigDecimal.valueOf(50_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000))
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL)
                        .plans(List.of(liquidity))
                        .build())
                .build();
        BigDecimal floorAsset = BigDecimal.valueOf(50_000_000);
        BigDecimal totalAsset = BigDecimal.valueOf(450_000_000);
        BigDecimal depletable = BigDecimal.valueOf(350_000_000);    // 단기버킷·바닥은 소진 대상 아님
        BigDecimal principalDepleted = depletable.multiply(new BigDecimal("0.65"));

        PlanCoverage pc = calculator.calculate(input).getPlanCoverages().get(0);

        // 단기 목돈은 원금 그대로(이자 무시) 노출된다
        assertThat(pc.getShortTermLumpSum()).isEqualByComparingTo(shortTermBucket);
        BigDecimal conserved = pc.getInheritanceAmount()
                .add(principalDepleted)
                .add(floorAsset)
                .add(pc.getShortTermLumpSum());
        assertThat(conserved).isEqualByComparingTo(totalAsset);
    }

    // ── 저배당: 보존 위험자산이 실질 자본성장으로 상속에 복리 반영 (compound() 성장경로) ──

    @Test
    void 저배당_보존위험자산은_실질자본성장률로_상속에_복리반영된다() {
        // 배당 0.5% → realCapitalGainRate = 4.5%(총수익) − 0.5%(배당) − 2%(물가) = 2% > 0
        //   → compound() 성장경로 진입(배당 3%면 음수라 0클램프되어 이 분기가 안 탔음).
        // 안전·연금·바닥을 0으로 두어 위험 보존분 성장만 상속에 남도록 분리한다.
        PlanAllocation lowDividend = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.valueOf(100_000_000))
                .surplusSafeAmount(BigDecimal.ZERO)
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(new BigDecimal("0.5000"))
                .holdings(List.of())
                .build();
        CoverageInput input = baseBuilder()
                .q3(0) // 상속우선: 소진비율 0.3 → 위험 보존분 0.7
                .pensionSaving(BigDecimal.ZERO)
                .floorAsset(BigDecimal.ZERO)
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL)
                        .plans(List.of(lowDividend))
                        .build())
                .build();

        PlanCoverage pc = calculator.calculate(input).getPlanCoverages().get(0);

        // 보존 위험원금 = 1억 × (1 − 0.3) = 7천만. 성장이 적용됐다면 상속이 원금을 초과해야 한다.
        BigDecimal preservedRiskPrincipal = BigDecimal.valueOf(70_000_000);
        assertThat(pc.getInheritanceAmount()).isGreaterThan(preservedRiskPrincipal);
        // 정확값 고정: 7천만 × (1 + 2%)^20 (remainingYears=20). 물가차감·지수가 바뀌면 깨지도록 상수 직기입.
        BigDecimal expected = preservedRiskPrincipal
                .multiply(BigDecimal.ONE.add(new BigDecimal("0.02")).pow(20))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(pc.getInheritanceAmount()).isEqualByComparingTo(expected);
    }

    // ── net 세액(실수령) 반영 ─────────────────────────────────────────────────

    @Test
    void 바닥자산_이자수입은_금융소득세_15_4퍼센트_차감후_실수령으로_계산된다() {
        // 바닥자산만 남기고(보존·이자만) 나머지 0 → 월수령 = 바닥 이자의 net 단일 흐름.
        PlanAllocation onlyFloor = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.ZERO)
                .surplusSafeAmount(BigDecimal.ZERO)
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(new BigDecimal("3.0000"))
                .holdings(List.of())
                .build();
        CoverageInput input = baseBuilder()
                .monthlyNationalPension(BigDecimal.ZERO)
                .pensionSaving(BigDecimal.ZERO)
                .floorAsset(BigDecimal.valueOf(120_000_000))
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL).plans(List.of(onlyFloor)).build())
                .build();

        PlanCoverage pc = calculator.calculate(input).getPlanCoverages().get(0);

        // 월 이자 = 1.2억 × 3.5%(SAFE_RATE) / 12 = 350,000(gross) → × (1−0.154) = 296,100(net)
        assertThat(pc.getMonthlyIncome()).isEqualByComparingTo("296100");
    }

    @Test
    void 사적연금_월수령은_연령별_연금소득세가_적용돼_고령일수록_실수령이_크다() {
        // 연금저축만 남기고 나머지 0 → 월수령 = 연금 흐름의 net 단일. 65세(5.5%) vs 80세(3.3%) 세율만 차이.
        BigDecimal income65 = pensionOnlyMonthlyIncome(65);
        BigDecimal income80 = pensionOnlyMonthlyIncome(80);

        assertThat(income80).isGreaterThan(income65);
    }

    private BigDecimal pensionOnlyMonthlyIncome(int age) {
        PlanAllocation onlyPension = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.ZERO)
                .surplusSafeAmount(BigDecimal.ZERO)
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(new BigDecimal("3.0000"))
                .holdings(List.of())
                .build();
        CoverageInput input = baseBuilder()
                .age(age)
                .monthlyNationalPension(BigDecimal.ZERO)
                .floorAsset(BigDecimal.ZERO)
                .pensionSaving(BigDecimal.valueOf(100_000_000))
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL).plans(List.of(onlyPension)).build())
                .build();
        return calculator.calculate(input).getPlanCoverages().get(0).getMonthlyIncome();
    }

    // ── #192 위험 소진분 자본차익 국내/해외 과세 구분 ─────────────────────────────

    @Test
    void 위험소진_자본차익은_국내주식형이면_비과세라_월수령이_해외보다_크다() {
        BigDecimal incomeForeign = riskOnlyMonthlyIncome(BigDecimal.ONE);   // 전액 해외 → 자본차익 15.4%
        BigDecimal incomeDomestic = riskOnlyMonthlyIncome(BigDecimal.ZERO); // 전액 국내주식형 → 자본차익 비과세
        assertThat(incomeDomestic).isGreaterThan(incomeForeign);
    }

    @Test
    void 위험소진_과세분가중_혼합이면_국내와_해외_사이의_월수령이다() {
        BigDecimal foreign = riskOnlyMonthlyIncome(BigDecimal.ONE);
        BigDecimal domestic = riskOnlyMonthlyIncome(BigDecimal.ZERO);
        BigDecimal blended = riskOnlyMonthlyIncome(new BigDecimal("0.6"));   // 해외 60%·국내 40%
        assertThat(blended).isGreaterThan(foreign).isLessThan(domestic);
    }

    @Test
    void 과세분가중_null이면_전액과세_1_0과_동일하다_기존동작_보존() {
        // 기존 테스트들이 riskCapGainTaxableWeight 미설정(null)이므로, null=1.0 폴백 동치를 명시 가드.
        BigDecimal nullWeight = riskOnlyMonthlyIncome(null);
        BigDecimal fullTaxable = riskOnlyMonthlyIncome(BigDecimal.ONE);
        assertThat(nullWeight).isEqualByComparingTo(fullTaxable);
    }

    @Test
    void 배당이_분배금이라_국내주식형이어도_배당분은_과세된다() {
        // 국내주식형(taxableWeight=0)이라도 분배금은 도미사일 무관 15.4% → 소진분 월수령이 무과세보다 작아야.
        // 무과세 가정(가상): 비교용으로 배당분까지 면제되면 안 된다는 걸, 배당률 0 대비로 검증한다.
        BigDecimal withDividend = riskOnlyMonthlyIncomeWithDividend(BigDecimal.ZERO, new BigDecimal("3.0000"));
        BigDecimal noDividend = riskOnlyMonthlyIncomeWithDividend(BigDecimal.ZERO, new BigDecimal("0.0001"));
        // 배당분이 과세되므로, 배당비중이 큰 쪽(withDividend)은 net이 더 깎인다(자본차익분은 둘 다 비과세).
        assertThat(withDividend).isLessThan(noDividend);
    }

    private BigDecimal riskOnlyMonthlyIncome(BigDecimal taxableWeight) {
        return riskOnlyMonthlyIncomeWithDividend(taxableWeight, new BigDecimal("2.0000"));
    }

    private BigDecimal riskOnlyMonthlyIncomeWithDividend(BigDecimal taxableWeight, BigDecimal dividendRate) {
        // 위험버킷만 남기고 q3=2(전액 소진) → 자본차익 실현분이 월수령에 최대로 반영되도록 분리.
        PlanAllocation riskOnly = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.valueOf(200_000_000))
                .surplusSafeAmount(BigDecimal.ZERO)
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(dividendRate) // < 4.5%(r)라 자본차익분 존재
                .riskCapGainTaxableWeight(taxableWeight)
                .holdings(List.of())
                .build();
        CoverageInput input = baseBuilder()
                .q3(2)
                .monthlyNationalPension(BigDecimal.ZERO)
                .pensionSaving(BigDecimal.ZERO)
                .floorAsset(BigDecimal.ZERO)
                .allocation(AllocationResult.builder()
                        .track(RecommendationTrack.NORMAL).plans(List.of(riskOnly)).build())
                .build();
        return calculator.calculate(input).getPlanCoverages().get(0).getMonthlyIncome();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private CoverageInput.CoverageInputBuilder baseBuilder() {
        return CoverageInput.builder()
                .allocation(normalAllocation())
                .q3(1)
                .age(65)
                .remainingYears(20)
                .targetLivingCost(BigDecimal.valueOf(3_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(1_000_000))
                .otherRegularIncome(BigDecimal.ZERO)
                .floorAsset(BigDecimal.valueOf(50_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000));
    }

    private AllocationResult normalAllocation() {
        PlanAllocation stable = PlanAllocation.builder()
                .type(PlanType.STABLE)
                .surplusRiskAmount(BigDecimal.valueOf(100_000_000))
                .surplusSafeAmount(BigDecimal.valueOf(200_000_000))
                .shortTermBucket(BigDecimal.ZERO)
                .planDividendRate(new BigDecimal("3.0000"))
                .holdings(List.of())
                .build();
        return AllocationResult.builder()
                .track(RecommendationTrack.NORMAL)
                .plans(List.of(stable))
                .build();
    }

    private Q3Scenario scenario(List<Q3Scenario> scenarios, int q3) {
        return scenarios.stream().filter(s -> s.q3() == q3).findFirst().orElseThrow();
    }
}
