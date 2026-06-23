package com.sol.user.mydata.dto;

import com.sol.user.trade.entity.TradeHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MydataTradeResponse(
        Long orderId,
        Long accountId,
        String tradeType,
        LocalDateTime tradedAt,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal tradeAmount,
        BigDecimal fee,
        Long productId
) {
    public static MydataTradeResponse from(TradeHistory trade) {
        return new MydataTradeResponse(
                trade.getOrderId(),
                trade.getAccount().getAccountId(),
                trade.getTradeType(),
                trade.getTradedAt(),
                trade.getQuantity(),
                trade.getAvgPrice(),
                trade.getTradeAmount(),
                trade.getFee(),
                trade.getProductId()
        );
    }
}
