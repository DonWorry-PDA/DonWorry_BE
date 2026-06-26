package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.debt.repository.DebtRepository;
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
import static org.mockito.BDDMockito.given;

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
        service = new AssetCompositionService(assetAggregator, depositDetailClient, debtRepository);
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

    /**
     * AssetSnapshot 레코드: (AssetBreakdown, List<Account>, List<HoldingWithProduct>, Map<Long, ProductBatchItem>)
     * cash 파라미터를 전체 예수금 합으로 사용해 grossTotal = cash + 0 + 0 + 0 = cash
     */
    private AssetAggregator.AssetSnapshot emptySnapshot(BigDecimal totalAsset) {
        // grossTotal() = cash + nonStockHoldingValue + pensionHoldingValue + stockHoldingValue
        // 단순화: cash = totalAsset, 나머지는 모두 0
        AssetBreakdown breakdown = new AssetBreakdown(
                totalAsset,     // cash
                BigDecimal.ZERO, // pensionCash
                BigDecimal.ZERO, // nonStockHoldingValue
                BigDecimal.ZERO, // pensionHoldingValue
                BigDecimal.ZERO  // stockHoldingValue
        );
        return new AssetAggregator.AssetSnapshot(breakdown, List.of(), List.of(), Map.of());
    }
}
