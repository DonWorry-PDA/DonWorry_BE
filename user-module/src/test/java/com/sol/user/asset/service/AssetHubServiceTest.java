package com.sol.user.asset.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedEvent;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedMonth;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.service.DividendScheduleService;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.stability.dto.LifeStabilityMetrics;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetHubServiceTest {

    private static final Long USER_ID = 1L;

    @Mock MonthlyCashFlowProjection projection;
    @Mock DividendScheduleService dividendScheduleService;
    @Mock CashFlowDiagnosisService cashFlowDiagnosisService;
    @Mock LifeStabilityService lifeStabilityService;
    @Mock AssetAggregator assetAggregator;
    @Mock SalaryPlanRepository salaryPlanRepository;
    @Spy AssetMapper assetMapper = new AssetMapper();

    @InjectMocks AssetHubService assetHubService;

    @BeforeEach
    void stubNoHoldingsByDefault() {
        // 예수금만 계약: 대부분 테스트는 보유종목 없음. ETF/주식 분포가 필요한 테스트만 override.
        lenient().when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        List.of(), List.of(), Map.of()));
        lenient().when(salaryPlanRepository.findByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        // 분배금 스케줄 소스 — 기본 0. 분배금 합산을 검증하는 테스트만 override.
        lenient().when(dividendScheduleService.monthlyGross(eq(USER_ID), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void 자산_분포는_카테고리별로_집계되고_비율_합은_100() {
        // 연금 68(IRP+연금저축) / 예금 20 / ETF 12(보유 ETF) = 총 1억
        List<Account> accounts = List.of(
                account("IRP", 60_000_000),
                account("DEPOSIT", 20_000_000),
                account("PENSION_SAVING", 8_000_000)
        );
        stubSnapshot(accounts, etfHolding(1001L, 12_000_000));
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.totalAsset()).isEqualByComparingTo("100000000");
        assertThat(response.allocation()).extracting(AssetAllocationItem::category)
                .containsExactly("연금", "예금", "ETF");
        assertThat(response.allocation()).extracting(AssetAllocationItem::ratio)
                .containsExactly(68, 20, 12);
        assertThat(response.allocation().stream().mapToInt(AssetAllocationItem::ratio).sum())
                .isEqualTo(100);
        assertThat(response.monthlyIncome()).isEqualByComparingTo("1300000");
        assertThat(response.monthlyExpense()).isEqualByComparingTo("2180000");
    }

    @Test
    void 이번_달_수입에_ETF_분배금이_더해진다() {
        // 분배금은 캘린더와 같은 스케줄 소스(DividendScheduleService)로 더해져야 한다 — 홈·리포트·캘린더 일치.
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();   // INCOME 이벤트 합(연금+이자) = 1,300,000
        stubLifeStability(59);
        when(dividendScheduleService.monthlyGross(eq(USER_ID), any())).thenReturn(BigDecimal.valueOf(200_000));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        // 1,300,000(이벤트) + 200,000(분배금) = 1,500,000
        assertThat(response.monthlyIncome()).isEqualByComparingTo("1500000");
    }

    @Test
    void 보유_개별주식은_주식_카테고리로_집계된다() {
        // 예금 8천 / 주식 2천 = 총 1억. productType=STOCK → 주식 버킷.
        List<Account> accounts = List.of(account("DEPOSIT", 80_000_000));
        stubSnapshot(accounts, holding(2001L, 20_000_000, "STOCK"));
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.allocation()).extracting(AssetAllocationItem::category)
                .containsExactly("예금", "주식");
        assertThat(response.allocation()).extracting(AssetAllocationItem::ratio)
                .containsExactly(80, 20);
    }

    @Test
    void totalAsset은_개별주식_DB평가액이_아닌_breakdown_현재가평가액을_사용한다() {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(2001L);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(8_000_000));
        ProductBatchItem product = new ProductBatchItem(2001L, "삼성전자", "STOCK", "005930");

        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        new AssetBreakdown(BigDecimal.valueOf(80_000_000), BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(30_000_000)),
                        List.of(account("DEPOSIT", 80_000_000)),
                        List.of(holding),
                        Map.of(2001L, product)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.totalAsset()).isEqualByComparingTo("110000000");
        assertThat(response.allocation()).extracting(AssetAllocationItem::category)
                .containsExactly("예금", "주식");
        assertThat(response.allocation()).extracting(AssetAllocationItem::ratio)
                .containsExactly(73, 27);
    }

    @Test
    void 상품메타데이터_누락_보유종목은_현재계약상_기타로_집계된다() {
        // 현 계약: product 배치 응답에 없으면 fromProductType(null) → ETC(기타) 폴백.
        // (fail-closed 전환은 별도 결정 — 본 테스트는 현재 동작을 고정한다.)
        List<Account> accounts = List.of(account("DEPOSIT", 90_000_000));
        stubSnapshotWithoutProduct(accounts, 3001L, 10_000_000);
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.allocation()).extracting(AssetAllocationItem::category)
                .containsExactly("예금", "기타");
        assertThat(response.allocation()).extracting(AssetAllocationItem::ratio)
                .containsExactly(90, 10);
    }

    @Test
    void 비율_보정으로_나누어떨어지지_않아도_합이_100() {
        List<Account> accounts = List.of(
                account("DEPOSIT", 1_000_000),
                account("BROKERAGE", 1_000_000),
                account("IRP", 1_000_000)
        );
        stubAccountsOnly(accounts);
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.allocation().stream().mapToInt(AssetAllocationItem::ratio).sum())
                .isEqualTo(100);
    }

    @Test
    void 월급만들기_달성률은_현금흐름_대비_목표생활비_비율() {
        stubCashFlow(1_300_000, 2_200_000); // 130/220 -> 59%
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().salaryMaking().achievementRate()).isEqualTo(59);
        assertThat(response.menus().lifeStability().coverageRate()).isEqualTo(59);
        assertThat(response.menus().lifeStability().grade()).isEqualTo("NEED_COMPLEMENT");
    }

    @Test
    void 자산_없으면_분포는_빈리스트_총액_0() {
        stubCashFlow(0, 0); // 목표 0 -> 달성률 null
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.totalAsset()).isEqualByComparingTo("0");
        assertThat(response.allocation()).isEmpty();
        assertThat(response.menus().salaryMaking().achievementRate()).isNull();
        assertThat(response.changeDirection()).isEqualTo("FLAT");
        assertThat(response.monthlyIncome()).isEqualByComparingTo("1300000");
        assertThat(response.monthlyExpense()).isEqualByComparingTo("2180000");
    }

    @Test
    void 생활안정도_미산출_사용자는_빈_미리보기() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        when(lifeStabilityService.getLatest(USER_ID))
                .thenThrow(new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().lifeStability().grade()).isNull();
        assertThat(response.menus().lifeStability().coverageRate()).isNull();
    }

    @Test
    void 생활안정도_RESOURCE_NOT_FOUND_외_예외는_삼키지_않고_전파() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        when(lifeStabilityService.getLatest(USER_ID))
                .thenThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> assetHubService.getHub(USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 투자건강검진_미리보기는_현금흐름자산_순자산_비율() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);
        // 현금 2천 / 현금흐름(비STOCK 보유) 3천 / 주식 5천 = 순자산 1억 → 30%
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        new AssetBreakdown(BigDecimal.valueOf(20_000_000), BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.valueOf(30_000_000), BigDecimal.ZERO, BigDecimal.valueOf(50_000_000)),
                        List.of(), List.of(), Map.of()));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().investmentCheck().cashflowAssetRatio()).isEqualTo(30);
    }

    @Test
    void 투자건강검진_미리보기는_상세와_같은_단일출처_largest_remainder를_쓴다() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);
        // 현금흐름·잠자는 돈·성장 각 1천만 = 33.33%씩 → CASHFLOW 34(상세 헤드라인과 동일 값).
        // 독립 HALF_UP(33)을 쓰면 허브·상세가 1%p 어긋난다.
        AssetBreakdown breakdown = new AssetBreakdown(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.valueOf(10_000_000));
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(breakdown, List.of(), List.of(), Map.of()));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().investmentCheck().cashflowAssetRatio())
                .isEqualTo(breakdown.cashflowAssetRatio())
                .isEqualTo(34);
    }

    @Test
    void 확정plan_있으면_월급만들기_hasActivePlan_true이고_ACTIVE로_조회한다() {
        stubMonthlyFlows();
        stubLifeStability(59);
        SalaryPlan plan = mock(SalaryPlan.class);
        when(plan.getExpectedMonthlySalary()).thenReturn(BigDecimal.valueOf(3_280_000));
        when(plan.getTargetMonthlyLivingCost()).thenReturn(BigDecimal.valueOf(3_000_000));
        when(plan.getLivingCostCoverageRate()).thenReturn(new BigDecimal("109.33"));
        when(salaryPlanRepository.findByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.of(plan));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().salaryMaking().hasActivePlan()).isTrue();
        assertThat(response.menus().salaryMaking().hasPlanHistory()).isTrue();
        assertThat(response.menus().salaryMaking().currentAmount()).isEqualByComparingTo("3280000");
        assertThat(response.menus().salaryMaking().targetAmount()).isEqualByComparingTo("3000000");
        assertThat(response.menus().salaryMaking().achievementRate()).isEqualTo(109);
        assertThat(response.menus().lifeStability().coverageRate()).isEqualTo(109);
        verify(salaryPlanRepository).findByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE);
    }

    @Test
    void 확정plan_없으면_월급만들기_hasActivePlan_false() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().salaryMaking().hasActivePlan()).isFalse();
        assertThat(response.menus().salaryMaking().hasPlanHistory()).isFalse();
        assertThat(response.menus().salaryMaking().currentAmount()).isEqualByComparingTo("1300000");
        assertThat(response.menus().salaryMaking().targetAmount()).isEqualByComparingTo("2200000");
        assertThat(response.menus().salaryMaking().achievementRate()).isEqualTo(59);
    }

    @Test
    void salaryPlanHistoryExistsWithoutActivePlanKeepsCurrentCashflowAndPlanHistoryFlag() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);
        when(salaryPlanRepository.existsByUserUserId(USER_ID)).thenReturn(true);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().salaryMaking().hasActivePlan()).isFalse();
        assertThat(response.menus().salaryMaking().hasPlanHistory()).isTrue();
        assertThat(response.menus().salaryMaking().currentAmount()).isEqualByComparingTo("1300000");
        assertThat(response.menus().salaryMaking().targetAmount()).isEqualByComparingTo("2200000");
        assertThat(response.menus().salaryMaking().achievementRate()).isEqualTo(59);
        assertThat(response.menus().lifeStability().coverageRate()).isEqualTo(59);
    }

    @Test
    void 후속이슈_의존_메뉴는_null_또는_기본값() {
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().investmentCheck().cashflowAssetRatio()).isNull();
        assertThat(response.menus().pensionDefer().deferYears()).isNull();
        assertThat(response.menus().monthlyReport().isNew()).isNull();
        assertThat(response.menus().retirementSim().available()).isTrue();
    }

    @Test
    void ETF_보유종목은_etfHoldings에_ticker와_quantity가_담긴다() {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(1001L);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(5_000_000));
        when(holding.getQuantity()).thenReturn(new BigDecimal("15.0000"));
        ProductBatchItem product = new ProductBatchItem(1001L, "SOL 미국배당다우존스", "ETF", "446720");

        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        breakdownPlaceholder(), List.of(),
                        List.of(holding), Map.of(1001L, product)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.etfHoldings()).hasSize(1);
        assertThat(response.etfHoldings().get(0).ticker()).isEqualTo("446720");
        assertThat(response.etfHoldings().get(0).quantity()).isEqualByComparingTo("15.0000");
        assertThat(response.etfSnapshotAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    void 비ETF_보유종목은_etfHoldings에_포함되지_않는다() {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(2001L);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(10_000_000));
        ProductBatchItem product = new ProductBatchItem(2001L, "삼성전자", "STOCK", null);

        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        breakdownPlaceholder(), List.of(),
                        List.of(holding), Map.of(2001L, product)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.etfHoldings()).isEmpty();
    }

    @Test
    void 동일_ETF를_여러_계좌에_보유하면_ticker_기준으로_수량이_합산된다() {
        HoldingWithProduct h1 = mock(HoldingWithProduct.class);
        when(h1.getProductId()).thenReturn(1001L);
        when(h1.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(3_000_000));
        when(h1.getQuantity()).thenReturn(new BigDecimal("10.0000"));

        HoldingWithProduct h2 = mock(HoldingWithProduct.class);
        when(h2.getProductId()).thenReturn(1001L);
        when(h2.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(2_000_000));
        when(h2.getQuantity()).thenReturn(new BigDecimal("7.0000"));

        ProductBatchItem product = new ProductBatchItem(1001L, "SOL 미국배당다우존스", "ETF", "446720");

        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        breakdownPlaceholder(), List.of(),
                        List.of(h1, h2), Map.of(1001L, product)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.etfHoldings()).hasSize(1);
        assertThat(response.etfHoldings().get(0).ticker()).isEqualTo("446720");
        assertThat(response.etfHoldings().get(0).quantity()).isEqualByComparingTo("17.0000");
    }

    @Test
    void 개별주식_보유종목은_stockHoldings에_ticker와_quantity가_담긴다() {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(2001L);
        when(holding.getQuantity()).thenReturn(new BigDecimal("100.0000"));
        ProductBatchItem product = new ProductBatchItem(2001L, "삼성전자", "STOCK", "005930");

        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);
        // 개별주 평가액 = 수량 × 실시간가(stockPrices). DB evaluationAmount(null)이 아니라 이 맵으로 계산(#305).
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(
                        breakdownPlaceholder(), List.of(),
                        List.of(holding), Map.of(2001L, product), Map.of(2001L, 80_000L)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.stockHoldings()).hasSize(1);
        assertThat(response.stockHoldings().get(0).ticker()).isEqualTo("005930");
        assertThat(response.stockHoldings().get(0).quantity()).isEqualByComparingTo("100.0000");
        // 100주 × 80,000원 = 8,000,000 (evaluationAmount가 아니라 실시간가 기반)
        assertThat(response.stockSnapshotAmount()).isEqualByComparingTo("8000000");
        // 개별주식은 ETF 스냅샷에 섞이지 않는다
        assertThat(response.etfHoldings()).isEmpty();
        assertThat(response.etfSnapshotAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 개별주식_보유가_없으면_stockHoldings는_빈_리스트다() {
        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.stockHoldings()).isNotNull().isEmpty();
        assertThat(response.stockSnapshotAmount()).isEqualByComparingTo("0");
    }

    @Test
    void ETF_보유가_없으면_etfHoldings는_빈_리스트다() {
        stubCashFlow(0, 0);
        stubMonthlyFlows();
        stubLifeStability(0);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.etfHoldings()).isNotNull().isEmpty();
        assertThat(response.etfSnapshotAmount()).isEqualByComparingTo("0");
    }

    private Account account(String accountType, long balance) {
        return new Account(null, accountType, "신한은행", "MOCK-ACC", BigDecimal.valueOf(balance), true);
    }

    private HoldingAndProduct etfHolding(long productId, long evaluationAmount) {
        return holding(productId, evaluationAmount, "ETF");
    }

    private HoldingAndProduct holding(long productId, long evaluationAmount, String productType) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(productId);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(evaluationAmount));
        return new HoldingAndProduct(holding, Map.of(productId,
                new ProductBatchItem(productId, "상품" + productId, productType, null)));
    }

    private void stubSnapshot(List<Account> accounts, HoldingAndProduct holdingAndProduct) {
        // breakdownFor가 mock getter를 호출하므로 when() 인자 평가 도중 stubbing이 끊기지 않게 미리 계산한다.
        List<HoldingWithProduct> holdings = List.of(holdingAndProduct.holding());
        AssetBreakdown breakdown = breakdownFor(accounts, holdings, holdingAndProduct.products());
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(breakdown, accounts, holdings, holdingAndProduct.products()));
    }

    private void stubSnapshotWithoutProduct(List<Account> accounts, long productId, long evaluationAmount) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(productId);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(evaluationAmount));
        AssetBreakdown breakdown = breakdownFor(accounts, List.of(holding), Map.of());
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(breakdown, accounts, List.of(holding), Map.of()));
    }

    private void stubAccountsOnly(List<Account> accounts) {
        AssetBreakdown breakdown = breakdownFor(accounts, List.of(), Map.of());
        when(assetAggregator.aggregateSnapshot(USER_ID)).thenReturn(
                new AssetAggregator.AssetSnapshot(breakdown, accounts, List.of(), Map.of()));
    }

    /** 카테고리 집계 테스트는 AssetBreakdown 값 자체를 보지 않으므로 영(zero)으로 둔다. */
    private AssetBreakdown breakdownPlaceholder() {
        return new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private AssetBreakdown breakdownFor(
            List<Account> accounts,
            List<HoldingWithProduct> holdings,
            Map<Long, ProductBatchItem> products
    ) {
        BigDecimal cash = accounts.stream()
                .map(a -> a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pensionCash = accounts.stream()
                .filter(a -> "IRP".equals(a.getAccountType()) || "PENSION_SAVING".equals(a.getAccountType()))
                .map(a -> a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nonStock = BigDecimal.ZERO;
        BigDecimal pensionHolding = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;

        for (HoldingWithProduct holding : holdings) {
            BigDecimal evaluationAmount = holding.getEvaluationAmount() == null
                    ? BigDecimal.ZERO : holding.getEvaluationAmount();
            ProductBatchItem product = products.get(holding.getProductId());
            if (product != null && "STOCK".equals(product.productType())) {
                stock = stock.add(evaluationAmount);
            } else if ("IRP".equals(holding.getAccountType()) || "PENSION_SAVING".equals(holding.getAccountType())) {
                pensionHolding = pensionHolding.add(evaluationAmount);
            } else {
                nonStock = nonStock.add(evaluationAmount);
            }
        }
        return new AssetBreakdown(cash, pensionCash, BigDecimal.ZERO, nonStock, pensionHolding, stock);
    }

    private record HoldingAndProduct(HoldingWithProduct holding, Map<Long, ProductBatchItem> products) {
    }

    private void stubCashFlow(long cashFlow, long target) {
        when(cashFlowDiagnosisService.diagnose(USER_ID)).thenReturn(CashFlowDiagnosisResponse.builder()
                .monthlyCashFlow(BigDecimal.valueOf(cashFlow))
                .targetMonthlyLivingCost(BigDecimal.valueOf(target))
                .monthlyShortfall(BigDecimal.ZERO)
                .shortfallExists(false)
                .build());
    }

    private void stubMonthlyFlows() {
        // 투영된 이번 달: 수입(연금) 1,300,000 / 지출(관리비) 2,180,000. sumFlow가 flowType별로 합산한다.
        lenient().when(projection.project(eq(USER_ID), any())).thenReturn(new ProjectedMonth(List.of(
                new ProjectedEvent("PENSION", "INCOME", BigDecimal.valueOf(1_300_000)),
                new ProjectedEvent("MAINTENANCE", "EXPENSE", BigDecimal.valueOf(2_180_000)))));
    }

    private void stubLifeStability(int coverageRate) {
        lenient().when(lifeStabilityService.getLatest(USER_ID)).thenReturn(LifeStabilityResponse.builder()
                .grade("NEED_COMPLEMENT")
                .gradeLabel("보완 필요")
                .metrics(LifeStabilityMetrics.builder()
                        .cashflowCoverageRate(BigDecimal.valueOf(coverageRate))
                        .build())
                .build());
    }
}
