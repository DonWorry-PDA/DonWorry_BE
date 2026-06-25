package com.sol.user.asset.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import com.sol.user.stability.dto.LifeStabilityMetrics;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetHubServiceTest {

    private static final Long USER_ID = 1L;

    @Mock AccountRepository accountRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock ProductBatchClient productBatchClient;
    @Mock CashFlowEventRepository cashFlowEventRepository;
    @Mock CashFlowDiagnosisService cashFlowDiagnosisService;
    @Mock LifeStabilityService lifeStabilityService;
    @Mock AssetAggregator assetAggregator;

    @InjectMocks AssetHubService assetHubService;

    @BeforeEach
    void stubNoHoldingsByDefault() {
        // 예수금만 계약: 대부분 테스트는 보유종목 없음. ETF/주식 분포가 필요한 테스트만 override.
        lenient().when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID))
                .thenReturn(List.of());
        // 투자 건강검진 미리보기는 별도 검증 — 기본은 빈 자산(순자산 0 → ratio null).
        lenient().when(assetAggregator.aggregate(USER_ID)).thenReturn(new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void 자산_분포는_카테고리별로_집계되고_비율_합은_100() {
        // 연금 68(IRP+연금저축) / 예금 20 / ETF 12(보유 ETF) = 총 1억
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of(
                account("IRP", 60_000_000),
                account("DEPOSIT", 20_000_000),
                account("PENSION_SAVING", 8_000_000)
        ));
        stubEtfHolding(1001L, 12_000_000);
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
    void 보유_개별주식은_주식_카테고리로_집계된다() {
        // 예금 8천 / 주식 2천 = 총 1억. productType=STOCK → 주식 버킷.
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of(
                account("DEPOSIT", 80_000_000)));
        stubHolding(2001L, 20_000_000, "STOCK");
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
    void 상품메타데이터_누락_보유종목은_현재계약상_기타로_집계된다() {
        // 현 계약: product 배치 응답에 없으면 fromProductType(null) → ETC(기타) 폴백.
        // (fail-closed 전환은 별도 결정 — 본 테스트는 현재 동작을 고정한다.)
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of(
                account("DEPOSIT", 90_000_000)));
        stubHoldingWithoutProduct(3001L, 10_000_000);
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
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of(
                account("DEPOSIT", 1_000_000),
                account("BROKERAGE", 1_000_000),
                account("IRP", 1_000_000)
        ));
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.allocation().stream().mapToInt(AssetAllocationItem::ratio).sum())
                .isEqualTo(100);
    }

    @Test
    void 월급만들기_달성률은_현금흐름_대비_목표생활비_비율() {
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
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
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
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
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
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
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
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
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);
        // 현금 2천 / 현금흐름(비STOCK 보유) 3천 / 주식 5천 = 순자산 1억 → 30%
        when(assetAggregator.aggregate(USER_ID)).thenReturn(new AssetBreakdown(
                BigDecimal.valueOf(20_000_000), BigDecimal.ZERO, BigDecimal.valueOf(30_000_000),
                BigDecimal.ZERO, BigDecimal.valueOf(50_000_000)));

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().investmentCheck().cashflowAssetRatio()).isEqualTo(30);
    }

    @Test
    void 후속이슈_의존_메뉴는_null_또는_기본값() {
        when(accountRepository.findByUserUserId(USER_ID)).thenReturn(List.of());
        stubCashFlow(1_300_000, 2_200_000);
        stubMonthlyFlows();
        stubLifeStability(59);

        AssetHubResponse response = assetHubService.getHub(USER_ID);

        assertThat(response.menus().investmentCheck().cashflowAssetRatio()).isNull();
        assertThat(response.menus().pensionDefer().deferYears()).isNull();
        assertThat(response.menus().monthlyReport().isNew()).isNull();
        assertThat(response.menus().retirementSim().available()).isTrue();
    }

    private Account account(String accountType, long balance) {
        return new Account(null, accountType, "신한은행", "MOCK-ACC", BigDecimal.valueOf(balance), true);
    }

    private void stubEtfHolding(long productId, long evaluationAmount) {
        stubHolding(productId, evaluationAmount, "ETF");
    }

    private void stubHolding(long productId, long evaluationAmount, String productType) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(productId);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(evaluationAmount));
        when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID)).thenReturn(List.of(holding));
        when(productBatchClient.fetchProducts(List.of(productId)))
                .thenReturn(Map.of(productId, new ProductBatchItem(productId, "상품" + productId, productType)));
    }

    private void stubHoldingWithoutProduct(long productId, long evaluationAmount) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(productId);
        when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(evaluationAmount));
        when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID)).thenReturn(List.of(holding));
        when(productBatchClient.fetchProducts(List.of(productId))).thenReturn(Map.of());
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
        lenient().when(cashFlowEventRepository.sumAmountByFlowTypeInPeriod(
                eq(USER_ID), eq("INCOME"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(1_300_000));
        lenient().when(cashFlowEventRepository.sumAmountByFlowTypeInPeriod(
                eq(USER_ID), eq("EXPENSE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(2_180_000));
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
