package com.sol.product.etf.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class EtfWebSocketConfig implements WebSocketConfigurer {

    private final EtfPriceWebSocketHandler etfPriceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(etfPriceWebSocketHandler, "/ws/etf/price")
                .setAllowedOrigins("*");
    }
}
