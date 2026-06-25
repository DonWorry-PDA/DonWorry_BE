package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentCheckServiceTest {

    private static final Long USER_ID = 1L;

    @Mock AssetAggregator assetAggregator;
    @Mock HoldingRepository holdingRepository;
    @Mock ProductBatchClient productBatchClient;

    @InjectMocks InvestmentCheckService service;

    @Test
    void 자산이_없으면_비율0_역할빈리스트_성장블록null() {
        when(assetAggregator.aggregate(USER_ID)).thenReturn(zeroBreakdown());

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isZero();
        assertThat(response.totalAsset()).isEqualByComparingTo("0");
        assertThat(response.roles()).isEmpty();
        assertThat(response.growthAsset()).isNull();
        // 개별주가 없으면 종목 분해 조회 자체를 하지 않는다.
        verifyNoInteractions(holdingRepository, productBatchClient);
    }

    @Test
    void 자산을_4역할로_분해하고_비율합은_100() {
        // 현금 3천(연금예수 1천 포함→잠자는 2천) / 현금흐름 3천 / 연금종목 1천 / 주식 3천 = 순자산 1억
        when(assetAggregator.aggregate(USER_ID)).thenReturn(new AssetBreakdown(
                won(30_000_000), won(10_000_000), won(30_000_000), won(10_000_000), won(30_000_000)));
        stubStocks(Map.of("삼성전자", 20_000_000L, "SK하이닉스", 10_000_000L));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isEqualTo(30); // 현금흐름 3천 / 순자산 1억
        assertThat(response.totalAsset()).isEqualByComparingTo("100000000");
        assertThat(response.roles()).extracting(RoleContribution::role)
                .containsExactly("CASHFLOW", "GROWTH", "IDLE", "PENSION");
        assertThat(response.roles()).extracting(RoleContribution::ratio)
                .containsExactly(30, 30, 20, 20);
        assertThat(response.roles().stream().mapToInt(RoleContribution::ratio).sum()).isEqualTo(100);

        RoleContribution cashflow = role(response, "CASHFLOW");
        // 3천만 × 0.035 / 12 = 87,500
        assertThat(cashflow.monthlyCashflow()).isEqualByComparingTo("87500");
        assertThat(role(response, "GROWTH").monthlyCashflow()).isEqualByComparingTo("0");
    }

    @Test
    void 금액이_0인_역할은_분해에서_제외된다() {
        // 잠자는 5천 / 성장 5천만. 현금흐름·연금 0 → 두 역할만.
        when(assetAggregator.aggregate(USER_ID)).thenReturn(new AssetBreakdown(
                won(50_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, won(50_000_000)));
        stubStocks(Map.of("삼성전자", 50_000_000L));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isZero();
        assertThat(response.roles()).extracting(RoleContribution::role)
                .containsExactly("GROWTH", "IDLE");
    }

    @Test
    void 성장블록_종목쏠림_최대비중과_레벨을_계산한다() {
        when(assetAggregator.aggregate(USER_ID)).thenReturn(stockOnlyBreakdown(40_000_000));
        // 삼성 1.2천 / SK 1천 / 현대 0.9천 / LG 0.9천 = 4천, 최대 30% → 낮음
        stubStocks(Map.of("삼성전자", 12_000_000L, "SK하이닉스", 10_000_000L,
                "현대차", 9_000_000L, "LG에너지솔루션", 9_000_000L));

        InvestmentCheckResponse.GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.amount()).isEqualByComparingTo("40000000");
        assertThat(growth.topStockName()).isEqualTo("삼성전자");
        assertThat(growth.concentrationRatio()).isEqualTo(30);
        assertThat(growth.concentrationLevel()).isEqualTo("낮음");
        // 숫자·현재배당 단정 없이 정성 멘트만.
        assertThat(growth.suggestion()).contains("배당 중심");
    }

    @Test
    void 단일종목만_보유하면_쏠림_높음() {
        when(assetAggregator.aggregate(USER_ID)).thenReturn(stockOnlyBreakdown(30_000_000));
        stubStocks(Map.of("삼성전자", 30_000_000L));

        InvestmentCheckResponse.GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.concentrationRatio()).isEqualTo(100);
        assertThat(growth.concentrationLevel()).isEqualTo("높음");
    }

    private RoleContribution role(InvestmentCheckResponse response, String role) {
        return response.roles().stream().filter(r -> r.role().equals(role)).findFirst().orElseThrow();
    }

    private AssetBreakdown zeroBreakdown() {
        return new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private AssetBreakdown stockOnlyBreakdown(long stock) {
        return new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, won(stock));
    }

    /** 종목명→평가액 맵으로 STOCK 보유 + product 배치 응답을 스텁한다. */
    private void stubStocks(Map<String, Long> byName) {
        List<HoldingWithProduct> holdings = new ArrayList<>();
        Map<Long, ProductBatchItem> products = new HashMap<>();
        long productId = 1000L;
        for (Map.Entry<String, Long> entry : byName.entrySet()) {
            long id = productId++;
            holdings.add(holding(id, entry.getValue()));
            products.put(id, new ProductBatchItem(id, entry.getKey(), "STOCK"));
        }
        lenient().when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID)).thenReturn(holdings);
        lenient().when(productBatchClient.fetchProducts(anyList())).thenReturn(products);
    }

    private HoldingWithProduct holding(long productId, long eval) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        lenient().when(holding.getProductId()).thenReturn(productId);
        lenient().when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(eval));
        return holding;
    }

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
