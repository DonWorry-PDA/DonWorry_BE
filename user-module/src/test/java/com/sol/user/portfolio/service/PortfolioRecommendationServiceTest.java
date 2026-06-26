package com.sol.user.portfolio.service;

import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import com.sol.user.portfolio.calculator.AlphaCoverageCalculator;
import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.calculator.PortfolioAllocationCalculator;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.mapper.PortfolioRecommendationMapper;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.type.AllocationRole;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.service.SurveyService;
import com.sol.user.trade.infra.rest.EtfPriceClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PortfolioRecommendationServiceTest {

    private final EtfPoolProvider etfPoolProvider = mock(EtfPoolProvider.class);
    private final OperationGradeInputAssembler inputAssembler = mock(OperationGradeInputAssembler.class);
    private final SurveyService surveyService = mock(SurveyService.class);
    private final CashFlowDiagnosisService cashFlowDiagnosisService = mock(CashFlowDiagnosisService.class);
    // #170 머지로 추가된 의존: 미스텁 시 빈 보유 → 차감 0(기존 동작 동일)
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    // 이체 필요액 계산용 BROKERAGE 예수금 조회 의존: 미스텁 시 빈 Optional → 잔고 0(holdings 매핑 무영향)
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    // #186: netHoldings의 1주 미만 필터용 현재가 조회 의존
    private final EtfPriceClient etfPriceClient = mock(EtfPriceClient.class);

    private final PortfolioRecommendationService service = new PortfolioRecommendationService(
            new OperationGradeCalculator(),
            new PortfolioAllocationCalculator(),
            new AlphaCoverageCalculator(),
            etfPoolProvider,
            new PortfolioRecommendationMapper(),
            inputAssembler,
            surveyService,
            cashFlowDiagnosisService,
            holdingRepository,
            accountRepository,
            etfPriceClient
    );

    @Test
    void STEP1부터6까지_조립되어_위험중립형_2안_추천이_나온다() {
        given(etfPoolProvider.getPool()).willReturn(pool());
        given(inputAssembler.assemble(eq(1L), any(SurveyAnswerResponse.class))).willReturn(neutralInput());
        given(surveyService.get(1L)).willReturn(
                SurveyAnswerResponse.builder().q1(2).q2(1).q3(1).build());
        // 화면 비교용 before 현금흐름 — toResponse가 충당률/부족액 계산에 사용
        given(cashFlowDiagnosisService.diagnose(1L)).willReturn(
                CashFlowDiagnosisResponse.builder()
                        .monthlyCashFlow(BigDecimal.valueOf(1_000_000))
                        .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                        .build());
        // 현재가 — 순매수액(백만 단위)을 충분히 밑도는 1주 값이라 모든 추천 종목이 매수 대상으로 유지됨
        given(etfPriceClient.getCurrentPrice(any())).willReturn(10_000L);

        RecommendationResponse response = service.recommend(1L);

        // mock 입력(위험중립형) → NORMAL, 2안(안정·유동성, 균형안 미제공)
        assertThat(response.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(response.getPlans()).extracting(PlanResponse::getType)
                .containsExactly(PlanType.STABLE, PlanType.LIQUIDITY);

        // 각 안에 배분+수령이 병합되어 있음
        assertThat(response.getPlans()).allSatisfy(plan -> {
            assertThat(plan.getMonthlyIncome()).isPositive();
            assertThat(plan.getAlphaCoverageRate()).isNotNull(); // NORMAL이라 충족률 존재
            assertThat(plan.getHoldings()).isNotEmpty();
            // 화면용 필드가 빚어져 있음
            assertThat(plan.getDisplayName()).isNotBlank();
            assertThat(plan.getStatus()).isNotNull();
            assertThat(plan.getAllocations()).isNotEmpty();
            // 안전버킷(바닥 포함)이 위험버킷만의 holdings 외에 별도 항목으로 포함됨
            assertThat(plan.getAllocations()).anySatisfy(
                    view -> assertThat(view.role()).isEqualTo(AllocationRole.SAFE));
        });

        // 추천은 정확히 1개 (NORMAL, 동점 시 STABLE 우선)
        assertThat(response.getPlans())
                .filteredOn(plan -> plan.getStatus() == PlanStatus.RECOMMENDED)
                .hasSize(1);

        // Q3 트레이드오프 표 + 기준 라벨
        assertThat(response.getQ3Scenarios()).hasSize(3);
        assertThat(response.getQ3ReferenceLabel()).isEqualTo("안정안 기준 예시");
        assertThat(response.getBand()).isNotNull();
    }

    @Test
    void 설문_미응답이면_추천이_실패한다() {
        // 설문 선행 필수 계약 — get()이 SURVEY_NOT_FOUND를 던지면 추천도 실패해야 한다
        given(surveyService.get(1L)).willThrow(new BaseException(ErrorCode.SURVEY_NOT_FOUND));

        assertThatThrownBy(() -> service.recommend(1L))
                .isInstanceOf(BaseException.class);
    }

    private OperationGradeInput neutralInput() {
        return OperationGradeInput.builder()
                .age(65)
                .totalAsset(BigDecimal.valueOf(600_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000))
                .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                .essentialRatio(new BigDecimal("0.72"))
                .monthlyNationalPension(BigDecimal.valueOf(1_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(500_000_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2)
                .q2(1)
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }

    private List<EtfInfo> pool() {
        return List.of(
                new EtfInfo(null, "446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(null, "452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo(null, "476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo(null, "438560", "SOL 국고채3년", 5,
                        new BigDecimal("3.00"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo(null, "436140", "SOL 종합채권(AA-이상)액티브", 5,
                        new BigDecimal("3.30"), "QUARTERLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED),
                new EtfInfo(null, "497880", "SOL CD금리MMF", 5,
                        new BigDecimal("3.20"), "MONTHLY", BucketRole.SAFE, CurrencyExposure.UNHEDGED)
        );
    }
}
