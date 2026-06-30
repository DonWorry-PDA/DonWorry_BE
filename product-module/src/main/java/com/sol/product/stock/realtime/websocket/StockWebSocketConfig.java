package com.sol.product.stock.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// @EnableWebSocket은 EtfWebSocketConfig에 이미 선언돼 있어, 모든 WebSocketConfigurer 빈이 수집된다.
@Configuration
@RequiredArgsConstructor
public class StockWebSocketConfig implements WebSocketConfigurer {

    private final StockPriceWebSocketHandler stockPriceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(stockPriceWebSocketHandler, "/ws/stock/price")
                .setAllowedOrigins("*");
    }
}
