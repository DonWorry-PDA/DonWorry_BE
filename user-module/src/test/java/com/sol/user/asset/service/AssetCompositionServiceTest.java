package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetHoldingItem;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetCompositionServiceTest {

    @Mock
    private AssetAggregator assetAggregator;
    @Mock
    private DepositDetailClient depositDetailClient;
    @Mock
    private DebtRepository debtRepository;

    private AssetCompositionService service;

    @BeforeEach
    void setUp() {
        service = new AssetCompositionService(assetAggregator, depositDetailClient, debtRepository, new AssetMapper());
    }

    @Test
    @DisplayName("netWorth는 totalAsset에서 totalDebt를 뺀 값이다")
    void getComposition_netWorthIsTotalAssetMinusDebt() {
        // Arrange
        given(assetAggregator.aggregateSnapshot(1L)).willReturn(emptySnapshot(new BigDecimal("10000000")));
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(new BigDecimal("3000000"));
        given(depositDetailClient.fetchDepositDetails(List.of())).willReturn(Map.of());

        // Act
        AssetCompositionResponse response = service.getComposition(1L);

        // Assert
        assertThat(response.getTotalAsset()).isEqualByComparingTo("10000000");
        assertThat(response.getTotalDebt()).isEqualByComparingTo("3000000");
        assertThat(response.getNetWorth()).isEqualByComparingTo("7000000");
    }

    @Test
    @DisplayName("groups 리스트는 null이 아니며 비어있을 수 있다")
    void getComposition_groupsIsNeverNull() {
        // Arrange
        given(assetAggregator.aggregateSnapshot(1L)).willReturn(emptySnapshot(BigDecimal.ZERO));
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        given(depositDetailClient.fetchDepositDetails(List.of())).willReturn(Map.of());

        // Act
        AssetCompositionResponse response = service.getComposition(1L);

        // Assert
        assertThat(response.getGroups()).isNotNull();
        assertThat(response.getAllocation()).isNotNull();
    }

    @Test
    @DisplayName("총자산이 0이면 allocation은 빈 리스트다")
    void getComposition_zeroTotalAsset_emptyAllocation() {
        // Arrange
        given(assetAggregator.aggregateSnapshot(1L)).willReturn(emptySnapshot(BigDecimal.ZERO));
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        given(depositDetailClient.fetchDepositDetails(List.of())).willReturn(Map.of());

        // Act
        AssetCompositionResponse response = service.getComposition(1L);

        // Assert
        assertThat(response.getAllocation()).isEmpty();
    }

    @Test
    @DisplayName("ETF 보유종목에는 tickerCode와 quantity가 채워진다")
    void getComposition_etfHolding_hasTickerCodeAndQuantity() {
        HoldingWithProduct holding = mockHolding(1L, 10L, "BROKERAGE",
                new BigDecimal("300000"), new BigDecimal("10.0000"));
        ProductBatchItem product = new ProductBatchItem(10L, "SOL 미국배당다우존스", "ETF", "446720");
        AssetAggregator.AssetSnapshot snapshot = snapshotWith(
                new BigDecimal("300000"), List.of(holding), Map.of(10L, product));

        given(assetAggregator.aggregateSnapshot(1L)).willReturn(snapshot);
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        lenient().when(depositDetailClient.fetchDepositDetails(anyList())).thenReturn(Map.of());

        AssetCompositionResponse response = service.getComposition(1L);

        AssetHoldingItem holdingItem = response.getGroups().stream()
                .flatMap(g -> g.getAccounts().stream())
                .flatMap(a -> a.getHoldings().stream())
                .findFirst()
                .orElseThrow();

        assertThat(holdingItem.getTickerCode()).isEqualTo("446720");
        assertThat(holdingItem.getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(holdingItem.getEvaluationAmount()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("비ETF 보유종목은 tickerCode가 null이다")
    void getComposition_nonEtfHolding_tickerCodeIsNull() {
        HoldingWithProduct holding = mockHolding(1L, 20L, "PENSION_SAVING",
                new BigDecimal("500000"), new BigDecimal("1.0000"));
        ProductBatchItem product = new ProductBatchItem(20L, "한국투자 TDF", "FUND", null);
        AssetAggregator.AssetSnapshot snapshot = snapshotWith(
                new BigDecimal("500000"), List.of(holding), Map.of(20L, product));

        given(assetAggregator.aggregateSnapshot(1L)).willReturn(snapshot);
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        lenient().when(depositDetailClient.fetchDepositDetails(anyList())).thenReturn(Map.of());

        AssetCompositionResponse response = service.getComposition(1L);

        AssetHoldingItem holdingItem = response.getGroups().stream()
                .flatMap(g -> g.getAccounts().stream())
                .flatMap(a -> a.getHoldings().stream())
                .findFirst()
                .orElseThrow();

        assertThat(holdingItem.getTickerCode()).isNull();
        assertThat(holdingItem.getQuantity()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("보유종목이 없는 계좌는 holdings 리스트가 비어있다")
    void getComposition_accountWithNoHoldings_emptyHoldingsList() {
        AssetAggregator.AssetSnapshot snapshot = snapshotWith(new BigDecimal("1000000"), List.of(), Map.of());
        given(assetAggregator.aggregateSnapshot(1L)).willReturn(snapshot);
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        lenient().when(depositDetailClient.fetchDepositDetails(anyList())).thenReturn(Map.of());

        AssetCompositionResponse response = service.getComposition(1L);

        long holdingCount = response.getGroups().stream()
                .flatMap(g -> g.getAccounts().stream())
                .mapToLong(a -> a.getHoldings().size())
                .sum();
        assertThat(holdingCount).isZero();
    }

    @Test
    @DisplayName("개별주(STOCK)는 수량×실시간가로 STOCK 그룹에 집계되고 그룹 합이 총자산과 일치한다 (#305)")
    void getComposition_stockHolding_valuedByRealtimePrice() {
        // 개별주는 evaluationAmount가 null이라 예전엔 STOCK 그룹이 0→누락돼 그룹합≠총자산이었다.
        HoldingWithProduct stock = mock(HoldingWithProduct.class);
        when(stock.getAccountId()).thenReturn(1L);
        when(stock.getProductId()).thenReturn(2001L);
        when(stock.getQuantity()).thenReturn(new BigDecimal("100"));
        ProductBatchItem product = new ProductBatchItem(2001L, "삼성전자", "STOCK", "005930");

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(1L);
        when(account.getAccountType()).thenReturn("BROKERAGE");
        when(account.getDepositBalance()).thenReturn(BigDecimal.ZERO);
        when(account.getInstitutionName()).thenReturn("신한투자증권");

        // breakdown.stock = 100 × 80,000 = 8,000,000 → totalAsset(grossTotal) 8,000,000
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("8000000"));
        AssetAggregator.AssetSnapshot snapshot = new AssetAggregator.AssetSnapshot(
                breakdown, List.of(account), List.of(stock),
                Map.of(2001L, product), Map.of(2001L, 80_000L));

        given(assetAggregator.aggregateSnapshot(1L)).willReturn(snapshot);
        given(debtRepository.sumBalanceByUserId(1L)).willReturn(BigDecimal.ZERO);
        lenient().when(depositDetailClient.fetchDepositDetails(anyList())).thenReturn(Map.of());

        AssetCompositionResponse response = service.getComposition(1L);

        AssetCompositionResponse.AssetGroupItem stockGroup = response.getGroups().stream()
                .filter(g -> "STOCK".equals(g.getCategory()))
                .findFirst().orElseThrow();
        assertThat(stockGroup.getTotalAmount()).isEqualByComparingTo("8000000");

        BigDecimal groupSum = response.getGroups().stream()
                .map(AssetCompositionResponse.AssetGroupItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(groupSum).isEqualByComparingTo(response.getTotalAsset());
    }

    /**
     * AssetSnapshot 레코드: (AssetBreakdown, List<Account>, List<HoldingWithProduct>, Map<Long, ProductBatchItem>)
     * cash 파라미터를 전체 예수금 합으로 사용해 grossTotal = cash + 0 + 0 + 0 = cash
     */
    private AssetAggregator.AssetSnapshot emptySnapshot(BigDecimal totalAsset) {
        AssetBreakdown breakdown = new AssetBreakdown(
                totalAsset,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        return new AssetAggregator.AssetSnapshot(breakdown, List.of(), List.of(), Map.of());
    }

    private AssetAggregator.AssetSnapshot snapshotWith(
            BigDecimal holdingTotal,
            List<HoldingWithProduct> holdings,
            Map<Long, ProductBatchItem> products) {
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                holdingTotal,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        String accountType = holdings.isEmpty() ? "DEPOSIT" : holdings.get(0).getAccountType();
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(1L);
        when(account.getAccountType()).thenReturn(accountType);
        when(account.getDepositBalance()).thenReturn(BigDecimal.ZERO);
        when(account.getInstitutionName()).thenReturn("신한투자증권");
        return new AssetAggregator.AssetSnapshot(breakdown, List.of(account), holdings, products);
    }

    private HoldingWithProduct mockHolding(Long holdingId, Long productId,
                                           String accountType, BigDecimal eval, BigDecimal quantity) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        when(holding.getProductId()).thenReturn(productId);
        when(holding.getAccountId()).thenReturn(1L);
        when(holding.getAccountType()).thenReturn(accountType);
        when(holding.getEvaluationAmount()).thenReturn(eval);
        when(holding.getQuantity()).thenReturn(quantity);
        return holding;
    }
}
