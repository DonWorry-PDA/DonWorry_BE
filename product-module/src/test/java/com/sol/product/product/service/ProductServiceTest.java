package com.sol.product.product.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.deposit.repository.DepositDetailRepository;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import com.sol.product.pensionsaving.repository.PensionSavingDetailRepository;
import com.sol.product.product.dto.ProductBatchItem;
import com.sol.product.product.entity.FinancialProduct;
import com.sol.product.product.mapper.ProductDetailMapper;
import com.sol.product.product.repository.FinancialProductRepository;
import com.sol.product.stock.entity.StockDetail;
import com.sol.product.stock.repository.StockDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private FinancialProductRepository financialProductRepository;
    @Mock
    private EtfDetailRepository etfDetailRepository;
    @Mock
    private StockDetailRepository stockDetailRepository;
    @Mock
    private DepositDetailRepository depositDetailRepository;
    @Mock
    private PensionSavingDetailRepository pensionSavingDetailRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private ProductDetailMapper productDetailMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                financialProductRepository,
                etfDetailRepository,
                stockDetailRepository,
                depositDetailRepository,
                pensionSavingDetailRepository,
                dailyPriceRepository,
                productDetailMapper
        );
    }

    @Test
    @DisplayName("상품 배치 조회 시 개별주식 tickerCode를 채운다")
    void getProductsByIds_stockProduct_fillsTickerCode() {
        FinancialProduct stockProduct = FinancialProduct.builder()
                .productId(2001L)
                .productName("삼성전자")
                .productType("STOCK")
                .build();
        StockDetail stockDetail = StockDetail.builder()
                .product(stockProduct)
                .tickerCode("005930")
                .build();

        given(financialProductRepository.findAllByProductIdIn(List.of(2001L)))
                .willReturn(List.of(stockProduct));
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(2001L)))
                .willReturn(List.of(stockDetail));

        List<ProductBatchItem> result = productService.getProductsByIds(List.of(2001L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(2001L);
        assertThat(result.get(0).productType()).isEqualTo("STOCK");
        assertThat(result.get(0).tickerCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("상품 배치 조회 시 ETF tickerCode가 없으면 상품 미존재 예외를 던진다")
    void getProductsByIds_etfProductWithoutTickerCode_throwsProductNotFound() {
        FinancialProduct etfProduct = FinancialProduct.builder()
                .productId(1001L)
                .productName("SOL 미국배당다우존스")
                .productType("ETF")
                .build();
        EtfDetail etfDetail = EtfDetail.builder()
                .product(etfProduct)
                .tickerCode(null)
                .build();

        given(financialProductRepository.findAllByProductIdIn(List.of(1001L)))
                .willReturn(List.of(etfProduct));
        given(etfDetailRepository.findAllByProductProductIdIn(List.of(1001L)))
                .willReturn(List.of(etfDetail));

        assertThatThrownBy(() -> productService.getProductsByIds(List.of(1001L)))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
