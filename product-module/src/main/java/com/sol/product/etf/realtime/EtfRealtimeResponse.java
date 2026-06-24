package com.sol.product.etf.realtime;

import java.math.BigDecimal;

public record EtfRealtimeResponse(
        String ticker,
        String productName,
        long currentPrice,
        long changePrice,
        BigDecimal changeRate,
        String sign          // 2=상승, 3=보합, 5=하락
) {}
