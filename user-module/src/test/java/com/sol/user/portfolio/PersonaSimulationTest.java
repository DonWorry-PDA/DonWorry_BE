package com.sol.user.portfolio;

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
import com.sol.user.portfolio.dto.PlanCoverage;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.Gender;
import com.sol.user.portfolio.type.InvestmentPropensity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STEP1~6 전체 파이프라인 페르소나 시뮬레이션. 트랙 분기를 검증하고 실제 수령액·α충족률을 출력한다.
 */
class PersonaSimulationTest {

    private final OperationGradeCalculator gradeCalc = new OperationGradeCalculator();
    private final PortfolioAllocationCalculator allocCalc = new PortfolioAllocationCalculator();
    private final AlphaCoverageCalculator coverageCalc = new AlphaCoverageCalculator();

    @Test
    void 페르소나_여유_ACTIVE_3안_정상충족() {
        CoverageResult r = run("여유(8억·ACTIVE)", active8(), 1, BigDecimal.ZERO);
        assertThat(r.getTrack().name()).isEqualTo("NORMAL");
    }

    @Test
    void 페르소나_구조적부족_자산부족() {
        CoverageResult r = run("구조적부족(2.5억)", shortage(), 1, BigDecimal.ZERO);
        assertThat(r.getTrack().name()).isEqualTo("STRUCTURAL_SHORTAGE");
        assertThat(r.getPlanCoverages()).isEmpty();
    }

    @Test
    void 페르소나_연금초과_PENSION_SUFFICIENT() {
        // 기타정기수입 120만 → α ≤ 0
        CoverageResult r = run("연금초과(기타수입)", pensionSufficient(), 1, BigDecimal.valueOf(1_200_000));
        assertThat(r.getTrack().name()).isEqualTo("PENSION_SUFFICIENT");
        assertThat(r.getPlanCoverages()).allSatisfy(pc -> assertThat(pc.getAlphaCoverageRate()).isNull());
    }

    @Test
    void 페르소나_빠듯_NEUTRAL_2안_부분충족() {
        CoverageResult r = run("빠듯(3.5억·NEUTRAL)", tightNeutral(), 1, BigDecimal.ZERO);
        assertThat(r.getTrack().name()).isEqualTo("NORMAL");
        assertThat(r.getPlanCoverages()).hasSize(2); // 위험중립형 2안
    }

    // ── 파이프라인 실행 + 출력 ───────────────────────────────────────────────────

    private CoverageResult run(String name, OperationGradeInput input, int q3, BigDecimal otherIncome) {
        OperationGradeResult grade = gradeCalc.calculate(input);

        AllocationInput allocInput = AllocationInput.builder()
                .finalGrade(grade.getFinalGrade())
                .surplus(grade.getSurplus())
                .floorAsset(grade.getFloorAsset())
                .totalAsset(input.totalAsset())
                .pensionSaving(input.pensionSaving())
                .propensity(input.investmentPropensity())
                .shortTermBucket(BigDecimal.ZERO)
                .pool(pool())
                .build();
        AllocationResult alloc = allocCalc.calculate(allocInput);

        CoverageInput covInput = CoverageInput.builder()
                .allocation(alloc)
                .q3(q3)
                .age(input.age())
                .remainingYears(grade.getRemainingYears())
                .targetLivingCost(input.targetMonthlyLivingCost())
                .monthlyNationalPension(input.monthlyNationalPension())
                .otherRegularIncome(otherIncome)
                .floorAsset(grade.getFloorAsset())
                .pensionSaving(input.pensionSaving())
                .build();
        CoverageResult cov = coverageCalc.calculate(covInput);

        print(name, grade, cov);
        return cov;
    }

    private void print(String name, OperationGradeResult grade, CoverageResult cov) {
        System.out.printf("PERSONA| %-18s track=%-20s grade=%d 남은햇수=%d 바닥=%,d 여유분=%,d α=%,d band=%s%n",
                name, cov.getTrack(), grade.getFinalGrade(), grade.getRemainingYears(),
                grade.getFloorAsset().longValue(), grade.getSurplus().longValue(),
                cov.getAlpha().longValue(), cov.getBand());
        for (PlanCoverage pc : cov.getPlanCoverages()) {
            System.out.printf("PERSONA|   %-10s 월수령=%,d α충족률=%s 상속=%,d%n",
                    pc.getType(), pc.getMonthlyIncome().longValue(),
                    pc.getAlphaCoverageRate() == null ? "N/A" : pc.getAlphaCoverageRate() + "%",
                    pc.getInheritanceAmount().longValue());
        }
    }

    // ── 페르소나 입력 ────────────────────────────────────────────────────────────

    private OperationGradeInput active8() {
        return base()
                .age(60).totalAsset(BigDecimal.valueOf(800_000_000)).pensionSaving(BigDecimal.valueOf(50_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(700_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(1_200_000))
                .investmentPropensity(InvestmentPropensity.ACTIVE)
                .build();
    }

    private OperationGradeInput shortage() {
        return base()
                .age(70).totalAsset(BigDecimal.valueOf(250_000_000)).pensionSaving(BigDecimal.valueOf(50_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(200_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(800_000))
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }

    private OperationGradeInput pensionSufficient() {
        return base()
                .age(65).totalAsset(BigDecimal.valueOf(400_000_000)).pensionSaving(BigDecimal.valueOf(50_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(350_000_000))
                .targetMonthlyLivingCost(BigDecimal.valueOf(2_500_000))
                .monthlyNationalPension(BigDecimal.valueOf(1_500_000))
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }

    private OperationGradeInput tightNeutral() {
        return base()
                .age(68).totalAsset(BigDecimal.valueOf(350_000_000)).pensionSaving(BigDecimal.valueOf(30_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(320_000_000))
                .monthlyNationalPension(BigDecimal.valueOf(1_300_000))
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }

    private OperationGradeInput.OperationGradeInputBuilder base() {
        return OperationGradeInput.builder()
                .gender(Gender.MALE)
                .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                .essentialRatio(new BigDecimal("0.72"))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2)
                .q2(1);
    }

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
