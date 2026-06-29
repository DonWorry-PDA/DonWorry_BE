package com.sol.product.stock.realtime;

import com.sol.product.stock.entity.StockDetail;
import com.sol.product.stock.repository.StockDetailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockClosingPriceSchedulerTest {

    @Mock StockTickerRegistry stockTickerRegistry;
    @Mock StockDetailRepository stockDetailRepository;
    @Mock StockRealtimeCache stockRealtimeCache;
    @Mock ClosingPriceSaver closingPriceSaver;

    @InjectMocks StockClosingPriceScheduler scheduler;

    @Test
    void 정상가격_파싱후_save호출() {
        StockDetail detail = stockDetail("005930");
        given(stockTickerRegistry.getAll()).willReturn(Set.of("005930"));
        given(stockDetailRepository.findAllByTickerCodeIn(anyList())).willReturn(List.of(detail));
        given(stockRealtimeCache.getRaw("005930"))
                .willReturn(Map.of("price", "75000", "change", "500", "drate", "0.67"));

        scheduler.saveClosingPrices();

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(closingPriceSaver).save(eq(detail), any(LocalDate.class),
                priceCaptor.capture(), any(BigDecimal.class), any(BigDecimal.class));
        assertThat(priceCaptor.getValue()).isEqualByComparingTo("75000");
    }

    @Test
    void 빈_가격필드_건너뜀_save미호출() {
        StockDetail detail = stockDetail("005930");
        given(stockTickerRegistry.getAll()).willReturn(Set.of("005930"));
        given(stockDetailRepository.findAllByTickerCodeIn(anyList())).willReturn(List.of(detail));
        given(stockRealtimeCache.getRaw("005930"))
                .willReturn(Map.of("price", "", "change", "500", "drate", "0.67"));

        scheduler.saveClosingPrices();

        verifyNoInteractions(closingPriceSaver);
    }

    @Test
    void 가격파싱실패_DB변경없이_나머지종목_계속처리() {
        StockDetail d1 = stockDetail("005930");
        StockDetail d2 = stockDetail("000660");
        given(stockTickerRegistry.getAll()).willReturn(Set.of("005930", "000660"));
        given(stockDetailRepository.findAllByTickerCodeIn(anyList())).willReturn(List.of(d1, d2));
        given(stockRealtimeCache.getRaw("005930"))
                .willReturn(Map.of("price", "NOT_A_NUMBER", "change", "0", "drate", "0"));
        given(stockRealtimeCache.getRaw("000660"))
                .willReturn(Map.of("price", "120000", "change", "1000", "drate", "0.84"));

        scheduler.saveClosingPrices();

        verify(closingPriceSaver, never()).save(eq(d1), any(), any(), any(), any());
        verify(closingPriceSaver, times(1)).save(eq(d2), any(), any(), any(), any());
    }

    @Test
    void 티커셋_비어있으면_조기종료() {
        given(stockTickerRegistry.getAll()).willReturn(Set.of());

        scheduler.saveClosingPrices();

        verifyNoInteractions(stockDetailRepository, closingPriceSaver);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private StockDetail stockDetail(String ticker) {
        // 스케줄러는 detail.getTickerCode()만 직접 사용(getProduct()는 mocked closingPriceSaver로 전달)
        StockDetail detail = mock(StockDetail.class);
        given(detail.getTickerCode()).willReturn(ticker);
        return detail;
    }
}
