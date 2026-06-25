package com.sol.user.portfolio.config;

import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CoreSlot;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.PropensityTier;
import com.sol.user.portfolio.type.SafeSlot;
import com.sol.user.portfolio.type.SafeSlotWeight;
import com.sol.user.portfolio.type.SlotWeight;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 추천(STEP5) 도메인 상수 — 화이트리스트·등급·role·환맵·위험비중표·안 구성을 한 곳에 응집(B1).
 *
 * <p>캘리브레이션 대상은 {@link #RISK_WEIGHT}뿐(mock 후 튜닝). 나머지(풀·role·환·안 구성)는 설계 확정값.
 */
public final class PortfolioConstants {

    private PortfolioConstants() {
    }

    // ── 화이트리스트 26종 + 위험등급(DB 부재 시 fallback) + role 수동매핑 ──────────

    /** ticker → 공시 위험등급 (신한 수집 확정값, DB 미적재 시 fallback). */
    public static final Map<String, Integer> RISK_GRADE = Map.ofEntries(
            // 위험자산 (주식형, 2~3등급)
            Map.entry("433330", 2),  // SOL 미국S&P500
            Map.entry("446720", 3),  // SOL 미국배당다우존스 (환노출)
            Map.entry("452360", 2),  // SOL 미국배당다우존스(H) (환헤지)
            Map.entry("476030", 2),  // SOL 미국나스닥100
            Map.entry("411540", 2),  // SOL 200Top10
            Map.entry("292500", 2),  // SOL KRX300
            Map.entry("399110", 2),  // SOL 미국S&P500ESG
            Map.entry("493420", 2),  // SOL 미국배당다우존스2호
            Map.entry("484880", 2),  // SOL 금융지주플러스고배당
            Map.entry("0152E0", 2),  // SOL 배당성향탑픽
            Map.entry("0105E0", 2),  // SOL 코리아고배당
            // 안전자산 (채권·예금성, 3~6등급, role로 안전 매핑)
            Map.entry("438560", 5),  // SOL 국고채3년
            Map.entry("438570", 4),  // SOL 국고채10년
            Map.entry("474390", 3),  // SOL 국고채30년
            Map.entry("488980", 5),  // SOL 회사채26-12
            Map.entry("0092C0", 5),  // SOL 회사채27-12
            Map.entry("0016X0", 5),  // SOL 중단기회사채
            Map.entry("436140", 5),  // SOL 종합채권(AA-이상)액티브
            Map.entry("0141T0", 5),  // SOL 중기종합채권
            Map.entry("469830", 5),  // SOL 초단기채권
            Map.entry("363510", 6),  // SOL 통안채
            Map.entry("497880", 5),  // SOL CD금리MMF
            Map.entry("484890", 5),  // SOL 머니마켓
            Map.entry("461600", 3),  // SOL 미국30년국채액티브(H)
            Map.entry("447620", 4),  // SOL 미국TOP5채권혼합
            Map.entry("0192S0", 4)   // SOL 코스피200채권혼합50
    );

    /** 추천 풀 화이트리스트 (= RISK_GRADE 키). */
    public static final List<String> WHITELIST = List.copyOf(RISK_GRADE.keySet());

    /** ticker → role (공시등급 아닌 수동매핑). 위험자산 11종, 나머지는 안전. */
    private static final List<String> RISK_TICKERS = List.of(
            "433330", "446720", "452360", "476030", "411540",
            "292500", "399110", "493420", "484880", "0152E0", "0105E0"
    );

    /** (H) 환헤지 ticker — etf_detail에 currency 컬럼이 없어 ticker로 판정. */
    private static final List<String> HEDGE_TICKERS = List.of("452360", "461600");

    // ── 권유가능등급 (성향별 최소 위험등급, product.riskGrade >= minGrade 이면 권유 가능) ──
    //    공격:1~6 적극:2~6 위험중립:3~6 안정추구:4~6 안정:5~6
    public static final Map<InvestmentPropensity, Integer> RECOMMENDABLE_MIN_GRADE = Map.of(
            InvestmentPropensity.AGGRESSIVE, 1,
            InvestmentPropensity.ACTIVE, 2,
            InvestmentPropensity.NEUTRAL, 3,
            InvestmentPropensity.STABLE_SEEKING, 4,
            InvestmentPropensity.STABLE, 5
    );

    // ── 위험비중표 (여유분 × 비중 = 위험목표). ★캘리브레이션 대상(초기값) ───────────────
    public static final Map<Integer, Map<PlanType, BigDecimal>> RISK_WEIGHT = Map.of(
            1, planWeights("0.10", "0.15", "0.08"),
            2, planWeights("0.20", "0.28", "0.15"),
            3, planWeights("0.32", "0.42", "0.25"),
            4, planWeights("0.45", "0.58", "0.38"),
            5, planWeights("0.58", "0.72", "0.50")
    );

    // ── STEP6 소진모델 가정치 — 한국 공인 장기가정 앵커(상품 무관 거시변수라 ETF 추천에도 적용) ──
    public static final BigDecimal SAFE_RATE = new BigDecimal("0.035");            // 안전금리(연, 분수)
    /**
     * 연금저축 기대수익률(연, 분수). 앵커: 연금저축 기본운용(TDF·밸런스드 default)을 위험자산·안전자산의
     * 중간 배분(≈50/50)으로 보고 NPS 위험총수익 4.5%·안전 3.5%의 중간값 4.0%로 둔다.
     * EXPECTED_TOTAL_RETURN(4.5%) 이하라 "연금이 위험버킷보다 고수익"인 모순이 없다(static 블록에서 가드).
     * (이전 5.0%는 위험버킷 총수익보다 높아 역전이었음 — 재앵커.)
     */
    public static final BigDecimal PENSION_SAVING_RATE = new BigDecimal("0.04");   // 연금저축수익률(연, 분수)
    /**
     * 위험버킷 장기 기대총수익률 r(배당+자본차익). 앵커: 국민연금 제5차 재정추계(2023) 장기
     * 기금투자수익률 가정 4.5%(인구 중위·거시 중립). 출처: 보건복지부 '제5차 국민연금 재정추계 결과'(2023.3).
     * (이전엔 Vanguard forward CMA 기반 5.0% — 국내 공인 앵커로 교체.)
     */
    public static final BigDecimal EXPECTED_TOTAL_RETURN = new BigDecimal("0.045");
    /**
     * 장기 기대물가(연). 앵커: 한국은행 물가안정목표 2.0%. 상속가치 실질 자본상승 계산에서 차감.
     */
    public static final BigDecimal EXPECTED_INFLATION = new BigDecimal("0.02");
    public static final int PENSION_WITHDRAWAL_MIN_AGE = 55;                       // 연금저축 인출 가능 연령

    /**
     * 대표 배당률(연, 분수) — 투자 건강검진(#2)의 현금흐름 추정·CTA("배당ETF로 옮기면 월 N원")용 프록시.
     * 앵커: 국내 배당형 ETF 평균 분배율 ≈3.5%(= SAFE_RATE 수준). 개별주 dividend_yield가 미적재(전 종목 NULL)라
     * 종목별 실분배를 못 쓰는 v1 상태의 근사치다. 정밀화 시 종목·ETF별 실분배(holdingRepository
     * .findDividendCalendarInputsByUserId)로 대체한다.
     */
    public static final BigDecimal REPRESENTATIVE_DIVIDEND_RATE = new BigDecimal("0.035");

    // ── STEP1~4(운용등급) 정책 기본값 — 유저 데이터 소스 없음, 정책으로 고정 ──────────────
    /**
     * 필수비율 — 목표생활비를 필수/재량으로 가르는 비율(STEP1 바닥자산·STEP2 buffer).
     * 근거: 국민연금연구원 국민노후보장패널(부부 최소217/적정297 ≈ 73%), 통계청 2024(최소240/적정336 ≈ 71%)의 중간값.
     */
    public static final BigDecimal ESSENTIAL_RATIO = new BigDecimal("0.72");
    /**
     * 기본 투자자성향 — 증권사 적합성 진단(KYC) 보유값이라 마이데이터·증권 연동 전엔 소스가 없어 위험중립형으로 고정.
     * 연동되면 증권사 값으로 대체하고, 권유가능등급 1차 필터(isRecommendable)도 함께 배선해야 한다.
     */
    public static final InvestmentPropensity DEFAULT_PROPENSITY = InvestmentPropensity.NEUTRAL;

    /** Q3(상속 vs 소비) → 여유분 소진비율. 0=상속우선 / 1=반반 / 2=소비우선. */
    public static final Map<Integer, BigDecimal> DEPLETION_RATIO = Map.of(
            0, new BigDecimal("0.3"),
            1, new BigDecimal("0.65"),
            2, new BigDecimal("1.0")
    );

    // ── 안 구성 (B안: 의미 슬롯 + tier별 해소) ─────────────────────────────────────

    /** 안별 위험버킷 코어 구성(슬롯 단위, tier 무관). 비중 합 = 1.0. */
    public static final Map<PlanType, List<SlotWeight>> PLAN_COMPOSITION = Map.of(
            PlanType.STABLE, List.of(
                    new SlotWeight(CoreSlot.HEDGED_CORE, BigDecimal.ONE)),
            PlanType.BALANCED, List.of(
                    new SlotWeight(CoreSlot.UNHEDGED_CORE, new BigDecimal("0.35")),
                    new SlotWeight(CoreSlot.HEDGED_CORE, new BigDecimal("0.35")),
                    new SlotWeight(CoreSlot.GROWTH, new BigDecimal("0.30"))),
            PlanType.LIQUIDITY, List.of(
                    new SlotWeight(CoreSlot.HEDGED_CORE, BigDecimal.ONE))
    );

    /** 코어 슬롯 → 실제 ticker (성향tier별 해소). 위험중립은 HEDGED_CORE를 환노출로 강등. */
    public static final Map<PropensityTier, Map<CoreSlot, String>> SLOT_TICKER = Map.of(
            PropensityTier.ACTIVE_PLUS, Map.of(
                    CoreSlot.HEDGED_CORE, "452360",     // 배당다우존스(H)
                    CoreSlot.UNHEDGED_CORE, "446720",   // 배당다우존스
                    CoreSlot.GROWTH, "476030"),         // 나스닥100
            PropensityTier.NEUTRAL, Map.of(
                    CoreSlot.HEDGED_CORE, "446720",     // (H) 권유불가 → 환노출로 강등
                    CoreSlot.UNHEDGED_CORE, "446720")   // GROWTH 없음(균형안 미제공)
    );

    /** tier별 제공 안 목록. 위험중립형은 균형안 미제공(2안). */
    public static final Map<PropensityTier, List<PlanType>> PLANS_BY_TIER = Map.of(
            PropensityTier.ACTIVE_PLUS, List.of(PlanType.STABLE, PlanType.BALANCED, PlanType.LIQUIDITY),
            PropensityTier.NEUTRAL, List.of(PlanType.STABLE, PlanType.LIQUIDITY)
    );

    // ── 안전·단기버킷 구성 (등급≥5만 사용 → 전 성향 적합, tier/안 무관 단일 규칙) ───────
    //    floor/surplus는 STEP6가 이미 다르게 다루는 경계(safeTarget = floorAsset + 여유안전).
    //    안별 차등은 floor 고정·여유안전 변동으로 자동 발생하므로 슬롯 비중을 안별로 두지 않는다.

    /** 안전 슬롯 → ticker (전부 등급≥5 — STABLE 적합성 cap 충족). */
    public static final Map<SafeSlot, String> SAFE_SLOT_TICKER = Map.of(
            SafeSlot.GOV, "438560",      // SOL 국고채3년 (5등급)
            SafeSlot.CREDIT, "436140",   // SOL 종합채권(AA-이상)액티브 (5등급)
            SafeSlot.CASH_EQ, "497880"   // SOL CD금리MMF (5등급, 예금 대체)
    );

    /** 바닥자산(원금보존) 구성 — 국고채70 + 예금성30. 비중 합=1.0. */
    public static final List<SafeSlotWeight> SAFE_FLOOR_COMPOSITION = List.of(
            new SafeSlotWeight(SafeSlot.GOV, new BigDecimal("0.70")),
            new SafeSlotWeight(SafeSlot.CASH_EQ, new BigDecimal("0.30"))
    );

    /** 여유안전(소진 대상) 구성 — 종합채권 중심 + 국고채 + 예금성. 비중 합=1.0. */
    public static final List<SafeSlotWeight> SAFE_SURPLUS_COMPOSITION = List.of(
            new SafeSlotWeight(SafeSlot.CREDIT, new BigDecimal("0.50")),
            new SafeSlotWeight(SafeSlot.GOV, new BigDecimal("0.30")),
            new SafeSlotWeight(SafeSlot.CASH_EQ, new BigDecimal("0.20"))
    );

    /** 단기버킷(유동성안 선확보) 구성 — 원금변동 없는 CD금리MMF 100%. 비중 합=1.0. */
    public static final List<SafeSlotWeight> SHORT_TERM_COMPOSITION = List.of(
            new SafeSlotWeight(SafeSlot.CASH_EQ, BigDecimal.ONE)
    );

    // ── 정적 일관성 검증 (B안 슬롯 구조 desync를 클래스 로딩 시 fail-fast) ──────────
    static {
        // 1) 각 안의 슬롯 비중 합은 1.0
        PLAN_COMPOSITION.forEach((plan, slots) -> {
            BigDecimal sum = slots.stream().map(SlotWeight::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalStateException("PLAN_COMPOSITION 비중 합이 1.0이 아님: " + plan + " = " + sum);
            }
        });
        // 2) tier별 제공 안의 모든 슬롯은 SLOT_TICKER로 해소 가능해야 함
        PLANS_BY_TIER.forEach((tier, plans) -> {
            Map<CoreSlot, String> slotMap = SLOT_TICKER.get(tier);
            for (PlanType plan : plans) {
                for (SlotWeight sw : PLAN_COMPOSITION.get(plan)) {
                    if (slotMap == null || !slotMap.containsKey(sw.slot())) {
                        throw new IllegalStateException(
                                "SLOT_TICKER 매핑 누락: tier=" + tier + ", plan=" + plan + ", slot=" + sw.slot());
                    }
                }
            }
        });
        // 3) 안전·단기버킷 sub-bucket 구성 검증 — 비중합=1.0 + ticker가 안전자산·화이트리스트·전성향 적합(등급≥5)
        int suitableMinGrade = Collections.max(RECOMMENDABLE_MIN_GRADE.values()); // = STABLE cap(5)
        validateSafeComposition("SAFE_FLOOR_COMPOSITION", SAFE_FLOOR_COMPOSITION, suitableMinGrade);
        validateSafeComposition("SAFE_SURPLUS_COMPOSITION", SAFE_SURPLUS_COMPOSITION, suitableMinGrade);
        validateSafeComposition("SHORT_TERM_COMPOSITION", SHORT_TERM_COMPOSITION, suitableMinGrade);
        // 4) 수익률 가정 정합성 — SAFE_RATE ≤ PENSION_SAVING_RATE ≤ EXPECTED_TOTAL_RETURN.
        //    연금저축이 위험버킷 총수익보다 높으면 "안전한 연금이 위험보다 고수익"인 모순이라 fail-fast.
        if (SAFE_RATE.compareTo(PENSION_SAVING_RATE) > 0
                || PENSION_SAVING_RATE.compareTo(EXPECTED_TOTAL_RETURN) > 0) {
            throw new IllegalStateException(
                    "수익률 가정 역전: SAFE_RATE(" + SAFE_RATE + ") ≤ PENSION_SAVING_RATE("
                            + PENSION_SAVING_RATE + ") ≤ EXPECTED_TOTAL_RETURN(" + EXPECTED_TOTAL_RETURN + ") 이어야 함");
        }
    }

    private static void validateSafeComposition(String name, List<SafeSlotWeight> composition, int suitableMinGrade) {
        BigDecimal sum = composition.stream().map(SafeSlotWeight::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException(name + " 비중 합이 1.0이 아님: " + sum);
        }
        for (SafeSlotWeight sw : composition) {
            String ticker = SAFE_SLOT_TICKER.get(sw.slot());
            if (ticker == null) {
                throw new IllegalStateException(name + " SAFE_SLOT_TICKER 매핑 누락: " + sw.slot());
            }
            if (!WHITELIST.contains(ticker)) {
                throw new IllegalStateException(name + " ticker가 화이트리스트에 없음: " + ticker);
            }
            if (roleOf(ticker) != BucketRole.SAFE) {
                throw new IllegalStateException(name + " ticker가 안전자산이 아님: " + ticker);
            }
            Integer grade = RISK_GRADE.get(ticker);
            if (grade == null || grade < suitableMinGrade) {
                // 등급<5면 STABLE 적합성 위반 — 안전버킷은 전 성향 적합 상품만 허용
                throw new IllegalStateException(
                        name + " ticker 등급이 전 성향 적합 기준(" + suitableMinGrade + ") 미만: " + ticker + " grade=" + grade);
            }
        }
    }

    // ── 접근 헬퍼 ─────────────────────────────────────────────────────────────────

    public static BucketRole roleOf(String ticker) {
        return RISK_TICKERS.contains(ticker) ? BucketRole.RISK : BucketRole.SAFE;
    }

    public static CurrencyExposure currencyOf(String ticker) {
        return HEDGE_TICKERS.contains(ticker) ? CurrencyExposure.HEDGED : CurrencyExposure.UNHEDGED;
    }

    public static boolean isRecommendable(InvestmentPropensity propensity, int riskGrade) {
        return riskGrade >= RECOMMENDABLE_MIN_GRADE.get(propensity);
    }

    public static BigDecimal riskWeight(int grade, PlanType plan) {
        return RISK_WEIGHT.get(grade).get(plan);
    }

    public static BigDecimal depletionRatio(int q3) {
        BigDecimal ratio = DEPLETION_RATIO.get(q3);
        if (ratio == null) {
            throw new IllegalArgumentException("유효하지 않은 q3 값: " + q3 + " (0,1,2 만 허용)");
        }
        return ratio;
    }

    public static String resolveTicker(PropensityTier tier, CoreSlot slot) {
        return SLOT_TICKER.get(tier).get(slot);
    }

    public static String resolveSafeTicker(SafeSlot slot) {
        return SAFE_SLOT_TICKER.get(slot);
    }

    private static Map<PlanType, BigDecimal> planWeights(String stable, String balanced, String liquidity) {
        return Map.of(
                PlanType.STABLE, new BigDecimal(stable),
                PlanType.BALANCED, new BigDecimal(balanced),
                PlanType.LIQUIDITY, new BigDecimal(liquidity)
        );
    }
}
