package com.sol.user.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BuyResponse(
        Long productId,
        BigDecimal quantity,
        Long executedPrice,
        BigDecimal totalAmount,
        LocalDateTime tradedAt
) {
}
