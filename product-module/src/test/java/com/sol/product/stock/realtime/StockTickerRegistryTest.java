package com.sol.product.stock.realtime;

import com.sol.product.stock.repository.StockDetailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockTickerRegistryTest {

    @Mock StockDetailRepository stockDetailRepository;

    @InjectMocks StockTickerRegistry registry;

    @Test
    void load_성공시_tickerSet이_채워진다() {
        given(stockDetailRepository.findAllTickerCodes()).willReturn(List.of("005930", "000660", ""));

        registry.load();

        assertThat(registry.getAll()).containsExactlyInAnyOrder("005930", "000660");
        assertThat(registry.contains("005930")).isTrue();
        assertThat(registry.contains("")).isFalse();
    }

    @Test
    void load_DB실패시_예외없이_tickerSet_공백유지() {
        given(stockDetailRepository.findAllTickerCodes()).willThrow(new RuntimeException("DB 연결 오류"));

        registry.load();

        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void reloadIfEmpty_tickerSet공백이면_load재시도() {
        given(stockDetailRepository.findAllTickerCodes())
                .willThrow(new RuntimeException("DB 오류"))
                .willReturn(List.of("005930"));

        registry.load();
        assertThat(registry.getAll()).isEmpty();

        registry.reloadIfEmpty();

        assertThat(registry.getAll()).containsExactly("005930");
        verify(stockDetailRepository, times(2)).findAllTickerCodes();
    }

    @Test
    void reloadIfEmpty_tickerSet이미있으면_재시도안함() {
        given(stockDetailRepository.findAllTickerCodes()).willReturn(List.of("005930"));

        registry.load();
        registry.reloadIfEmpty();

        verify(stockDetailRepository, times(1)).findAllTickerCodes();
    }
}
