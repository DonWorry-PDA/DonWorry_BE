package com.sol.user.portfolio.calibration;

import com.sol.user.portfolio.calculator.AlphaCoverageCalculator;
import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.calculator.PortfolioAllocationCalculator;
import com.sol.user.portfolio.dto.AllocationInput;
import com.sol.user.portfolio.dto.AllocationResult;
import com.sol.user.portfolio.dto.CoverageInput;
import com.sol.user.portfolio.dto.CoverageResult;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResult;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.mapper.PortfolioRecommendationMapper;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.Gender;
import com.sol.user.portfolio.type.InvestmentPropensity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 캘리브레이션 입력 스윕 하니스 (계수 고정, 입력 그리드 대량 실행 → CSV 덤프).
 *
 * <p>마이데이터/DB/HTTP를 모두 우회하고 STEP1~6 calculator를 직접 조립한다.
 * (PortfolioRecommendationService의 toAllocationInput/toCoverageInput을 복제 — 그쪽은 private)
 *
 * <p>실행: ./gradlew :user-module:test --tests "com.sol.user.portfolio.calibration.CalibrationHarness"
 * 결과: user-module/build/calibration/sweep.csv
 *
 * <p>계수 튜닝은 PortfolioConstants(SAFE_RATE·PENSION_SAVING_RATE·depletionRatio 등)·
 * OperationGradeCalculator 가중치를 바꾼 뒤 재실행해 CSV를 비교한다.
 */
class CalibrationHarness {

    private final OperationGradeCalculator operationGradeCalculator = new OperationGradeCalculator();
    private final PortfolioAllocationCalculator allocationCalculator = new PortfolioAllocationCalculator();
    private final AlphaCoverageCalculator coverageCalculator = new AlphaCoverageCalculator();
    private final PortfolioRecommendationMapper mapper = new PortfolioRecommendationMapper();

    // service의 mock 상수와 동일 (입력 스윕 대상이 아닌 값은 고정)
    private static final int Q3 = 1;
    private static final BigDecimal MOCK_OTHER_REGULAR_INCOME = BigDecimal.ZERO;
    private static final BigDecimal MOCK_SHORT_TERM_BUCKET = BigDecimal.ZERO;

    // ── 스윕 그리드 ──
    private static final int[] AGES = {60, 65, 70};
    private static final InvestmentPropensity[] PROPENSITIES = {
            InvestmentPropensity.STABLE, InvestmentPropensity.NEUTRAL, InvestmentPropensity.AGGRESSIVE
    };
    private static final long[] TOTAL_ASSETS = {300_000_000L, 600_000_000L, 900_000_000L, 1_200_000_000L};
    // 저생활비(100만) + 고연금(200/250만) 셀로 PENSION_SUFFICIENT(α≤0) 경로 커버
    private static final long[] LIVING_COSTS = {1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L};
    private static final long[] NATIONAL_PENSIONS = {500_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L};
    private static final long[] PENSION_SAVINGS = {0L, 50_000_000L};

    @Test
    void sweep() throws IOException {
        List<EtfInfo> pool = pool();
        StringBuilder csv = new StringBuilder();
        csv.append("age,propensity,totalAsset,livingCost,nationalPension,pensionSaving,")
           .append("track,band,alpha,finalGrade,surplus,floorAsset,")
           .append("planType,status,displayName,monthlyIncome,alphaCoverageRate,inheritanceAmount\n");

        int rows = 0;
        int errors = 0;
        for (int age : AGES) {
            for (InvestmentPropensity propensity : PROPENSITIES) {
                for (long totalAsset : TOTAL_ASSETS) {
                    for (long livingCost : LIVING_COSTS) {
                        for (long nationalPension : NATIONAL_PENSIONS) {
                            for (long pensionSaving : PENSION_SAVINGS) {
                                OperationGradeInput input = buildInput(
                                        age, propensity, totalAsset, livingCost, nationalPension, pensionSaving);
                                String key = age + "," + propensity + "," + totalAsset + ","
                                        + livingCost + "," + nationalPension + "," + pensionSaving + ",";
                                try {
                                    OperationGradeResult grade = operationGradeCalculator.calculate(input);
                                    AllocationResult allocation =
                                            allocationCalculator.calculate(toAllocationInput(grade, input, pool));
                                    CoverageResult coverage =
                                            coverageCalculator.calculate(toCoverageInput(allocation, grade, input));
                                    RecommendationResponse resp = mapper.toResponse(allocation, coverage);

                                    String trackCols = nz(resp.getTrack()) + "," + nz(resp.getBand()) + ","
                                            + nz(resp.getAlpha()) + ","
                                            + nz(grade.getFinalGrade()) + "," + nz(grade.getSurplus()) + ","
                                            + nz(grade.getFloorAsset()) + ",";

                                    if (resp.getPlans().isEmpty()) {
                                        csv.append(key).append(trackCols)
                                           .append(",,,,,\n"); // 구조적 부족: plan 없음
                                        rows++;
                                    } else {
                                        for (PlanResponse p : resp.getPlans()) {
                                            csv.append(key).append(trackCols)
                                               .append(nz(p.getType())).append(",")
                                               .append(nz(p.getStatus())).append(",")
                                               .append(nz(p.getDisplayName())).append(",")
                                               .append(nz(p.getMonthlyIncome())).append(",")
                                               .append(nz(p.getAlphaCoverageRate())).append(",")
                                               .append(nz(p.getInheritanceAmount())).append("\n");
                                            rows++;
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace(); // 스택트레이스는 stderr에 보존, CSV엔 요약만
                                    String msg = String.valueOf(e.getMessage())
                                            .replaceAll("[,\n\r\"]", " ");
                                    csv.append(key).append("ERROR,,,,,,,,")
                                       .append(e.getClass().getSimpleName()).append(":")
                                       .append(msg).append(",\n");
                                    errors++;
                                }
                            }
                        }
                    }
                }
            }
        }

        Path out = Path.of("build/calibration/sweep.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv.toString());
        System.out.println("[calibration] wrote " + rows + " rows (" + errors + " errors) → "
                + out.toAbsolutePath());
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString();
    }

    private OperationGradeInput buildInput(int age, InvestmentPropensity propensity, long totalAsset,
                                           long livingCost, long nationalPension, long pensionSaving) {
        BigDecimal total = BigDecimal.valueOf(totalAsset);
        // 가용 금융자산은 총자산의 ~83% 근사(mock 비율 600M→500M). 스윕 대상 아님.
        BigDecimal available = total.multiply(new BigDecimal("0.83")).setScale(0, RoundingMode.HALF_UP);
        return OperationGradeInput.builder()
                .age(age)
                .gender(Gender.MALE)
                .totalAsset(total)
                .pensionSaving(BigDecimal.valueOf(pensionSaving))
                .targetMonthlyLivingCost(BigDecimal.valueOf(livingCost))
                .essentialRatio(new BigDecimal("0.72"))
                .monthlyNationalPension(BigDecimal.valueOf(nationalPension))
                .availableFinancialAsset(available)
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2)
                .q2(1)
                .investmentPropensity(propensity)
                .build();
    }

    // ── PortfolioRecommendationService private 로직 복제 ──

    private AllocationInput toAllocationInput(OperationGradeResult grade, OperationGradeInput input, List<EtfInfo> pool) {
        return AllocationInput.builder()
                .finalGrade(grade.getFinalGrade())
                .surplus(grade.getSurplus())
                .floorAsset(grade.getFloorAsset())
                .totalAsset(input.totalAsset())
                .pensionSaving(input.pensionSaving())
                .propensity(input.investmentPropensity())
                .shortTermBucket(MOCK_SHORT_TERM_BUCKET)
                .pool(pool)
                .build();
    }

    private CoverageInput toCoverageInput(AllocationResult allocation, OperationGradeResult grade, OperationGradeInput input) {
        return CoverageInput.builder()
                .allocation(allocation)
                .q3(Q3)
                .age(input.age())
                .remainingYears(grade.getRemainingYears())
                .targetLivingCost(input.targetMonthlyLivingCost())
                .monthlyNationalPension(input.monthlyNationalPension())
                .otherRegularIncome(MOCK_OTHER_REGULAR_INCOME)
                .floorAsset(grade.getFloorAsset())
                .pensionSaving(input.pensionSaving())
                .build();
    }

    /** 고정 ETF 풀 fixture (ServiceTest 패턴). 캘리 시 입력만 스윕하므로 풀은 고정. */
    private List<EtfInfo> pool() {
        return List.of(
                new EtfInfo("446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo("452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo("476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED)
        );
    }
}
