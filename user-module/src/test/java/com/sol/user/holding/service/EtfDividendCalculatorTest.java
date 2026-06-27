package com.sol.user.holding.service;

import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EtfDividendCalculatorTest {

    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final ProductBatchClient productBatchClient = mock(ProductBatchClient.class);

    private final EtfDividendCalculator calculator =
            new EtfDividendCalculator(holdingRepository, productBatchClient);

    @Test
    void 보유ETF의_월_분배금을_종목별_단가x수량으로_합산한다() {
        given(holdingRepository.findAllHoldingsByUserId(1L)).willReturn(List.of(
                holding(101L, new BigDecimal("10")),
                holding(102L, new BigDecimal("5"))
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of(
                101L, new BigDecimal("10000"),  // 10주 × 10,000 = 100,000
                102L, new BigDecimal("4000")    // 5주 × 4,000 = 20,000
        ));

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("120000");
    }

    @Test
    void 보유종목이_없으면_0이고_상품조회를_호출하지_않는다() {
        given(holdingRepository.findAllHoldingsByUserId(1L)).willReturn(List.of());

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("0");
        verifyNoInteractions(productBatchClient);
    }

    @Test
    void 분배금_정보가_없는_종목은_0으로_처리한다() {
        given(holdingRepository.findAllHoldingsByUserId(1L)).willReturn(List.of(
                holding(101L, new BigDecimal("10"))
        ));
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of());

        assertThat(calculator.monthlyDividend(1L)).isEqualByComparingTo("0");
    }

    private EtfHolding holding(Long productId, BigDecimal quantity) {
        return new EtfHolding() {
            @Override public Long getHoldingId() { return productId; }
            @Override public Long getProductId() { return productId; }
            @Override public BigDecimal getQuantity() { return quantity; }
        };
    }
}
