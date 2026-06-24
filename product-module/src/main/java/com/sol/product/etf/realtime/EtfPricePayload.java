package com.sol.product.etf.realtime;

import java.math.BigDecimal;

/**
 * 프론트 WebSocket push 전용 payload.
 * sign: 2=상승, 3=보합, 5=하락
 */
public record EtfPricePayload(
        String ticker,
        long currentPrice,
        long changePrice,
        BigDecimal changeRate,
        String sign
) {
    public static EtfPricePayload of(String ticker, String price, String change, String drate, String sign) {
        if (price == null || price.isBlank() || change == null || change.isBlank() || drate == null || drate.isBlank()) {
            return null;
        }
        return new EtfPricePayload(
                ticker,
                Long.parseLong(price.trim()),
                Long.parseLong(change.trim()),
                new BigDecimal(drate.trim()),
                sign != null ? sign.trim() : "3"
        );
    }
}
