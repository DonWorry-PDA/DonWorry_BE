package com.sol.product.stock.realtime;

import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.product.entity.FinancialProduct;
import com.sol.product.stock.entity.StockDetail;
import com.sol.product.stock.repository.StockDetailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

    @Mock StockDetailRepository stockDetailRepository;
    @Mock StockRealtimeCache stockRealtimeCache;
    @Mock DailyPriceRepository dailyPriceRepository;

    @InjectMocks StockPriceService service;

    // ── getBatchPrices ──────────────────────────────────────────────────────

    @Test
    void getBatchPrices_Redis캐시히트_daily_price미조회() {
        StockDetail detail = stockDetail(1L, "005930");
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(1L))).willReturn(List.of(detail));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of("price", "75000"));

        Map<Long, Long> result = service.getBatchPrices(List.of(1L));

        assertThat(result).containsEntry(1L, 75_000L);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void getBatchPrices_Redis미스_배치daily_price쿼리1회() {
        StockDetail d1 = stockDetail(1L, "005930");
        StockDetail d2 = stockDetail(2L, "000660");
        DailyPrice dp1 = dailyPrice(1L, 75_000);
        DailyPrice dp2 = dailyPrice(2L, 120_000);
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(1L, 2L))).willReturn(List.of(d1, d2));
        given(stockRealtimeCache.getRaw(any())).willReturn(Map.of());
        given(dailyPriceRepository.findLatestByProductIds(List.of(1L, 2L))).willReturn(List.of(dp1, dp2));

        Map<Long, Long> result = service.getBatchPrices(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 75_000L).containsEntry(2L, 120_000L);
        verify(dailyPriceRepository, times(1)).findLatestByProductIds(List.of(1L, 2L));
    }

    @Test
    void getBatchPrices_Redis가격형식오류_daily_price폴백() {
        StockDetail detail = stockDetail(1L, "005930");
        DailyPrice dp = dailyPrice(1L, 74_000);
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(1L))).willReturn(List.of(detail));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of("price", "INVALID"));
        given(dailyPriceRepository.findLatestByProductIds(List.of(1L))).willReturn(List.of(dp));

        Map<Long, Long> result = service.getBatchPrices(List.of(1L));

        assertThat(result).containsEntry(1L, 74_000L);
    }

    @Test
    void getBatchPrices_일부Redis히트_일부daily_price폴백() {
        StockDetail d1 = stockDetail(1L, "005930");
        StockDetail d2 = stockDetail(2L, "000660");
        DailyPrice dp2 = dailyPrice(2L, 120_000);
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(1L, 2L))).willReturn(List.of(d1, d2));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of("price", "75000"));
        given(stockRealtimeCache.getRaw("000660")).willReturn(Map.of());
        given(dailyPriceRepository.findLatestByProductIds(List.of(2L))).willReturn(List.of(dp2));

        Map<Long, Long> result = service.getBatchPrices(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 75_000L).containsEntry(2L, 120_000L);
        verify(dailyPriceRepository).findLatestByProductIds(List.of(2L));
    }

    @Test
    void getBatchPrices_stockDetail없는_productId는_0반환() {
        given(stockDetailRepository.findAllByProductProductIdIn(List.of(99L))).willReturn(List.of());

        Map<Long, Long> result = service.getBatchPrices(List.of(99L));

        assertThat(result).containsEntry(99L, 0L);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void getBatchPrices_빈목록_빈Map반환() {
        Map<Long, Long> result = service.getBatchPrices(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(stockDetailRepository, dailyPriceRepository);
    }

    // ── getCurrentPriceByProductId ──────────────────────────────────────────

    @Test
    void getCurrentPriceByProductId_Redis캐시히트() {
        StockDetail detail = stockDetail(1L, "005930");
        given(stockDetailRepository.findByProductProductId(1L)).willReturn(Optional.of(detail));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of("price", "75000"));

        long price = service.getCurrentPriceByProductId(1L);

        assertThat(price).isEqualTo(75_000L);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void getCurrentPriceByProductId_Redis형식오류_daily_price폴백() {
        StockDetail detail = stockDetail(1L, "005930");
        DailyPrice dp = dailyPrice(1L, 74_000);
        given(stockDetailRepository.findByProductProductId(1L)).willReturn(Optional.of(detail));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of("price", "INVALID"));
        given(dailyPriceRepository.findTopByProductProductIdOrderByPriceDateDesc(1L)).willReturn(Optional.of(dp));

        long price = service.getCurrentPriceByProductId(1L);

        assertThat(price).isEqualTo(74_000L);
    }

    @Test
    void getCurrentPriceByProductId_Redis미스_daily_price반환() {
        StockDetail detail = stockDetail(1L, "005930");
        DailyPrice dp = dailyPrice(1L, 74_500);
        given(stockDetailRepository.findByProductProductId(1L)).willReturn(Optional.of(detail));
        given(stockRealtimeCache.getRaw("005930")).willReturn(Map.of());
        given(dailyPriceRepository.findTopByProductProductIdOrderByPriceDateDesc(1L)).willReturn(Optional.of(dp));

        long price = service.getCurrentPriceByProductId(1L);

        assertThat(price).isEqualTo(74_500L);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private StockDetail stockDetail(Long productId, String ticker) {
        FinancialProduct product = mock(FinancialProduct.class);
        // lenient: getBatchPrices는 getProduct().getProductId() 필요, getCurrentPriceByProductId는 불필요
        lenient().when(product.getProductId()).thenReturn(productId);
        StockDetail detail = mock(StockDetail.class);
        lenient().when(detail.getTickerCode()).thenReturn(ticker);
        lenient().when(detail.getProduct()).thenReturn(product);
        return detail;
    }

    private DailyPrice dailyPrice(Long productId, long closingPrice) {
        FinancialProduct product = mock(FinancialProduct.class);
        lenient().when(product.getProductId()).thenReturn(productId);
        DailyPrice dp = mock(DailyPrice.class);
        lenient().when(dp.getProduct()).thenReturn(product);
        lenient().when(dp.getClosingPrice()).thenReturn(BigDecimal.valueOf(closingPrice));
        return dp;
    }
}
