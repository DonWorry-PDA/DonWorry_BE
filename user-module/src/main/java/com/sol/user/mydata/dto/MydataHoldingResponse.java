package com.sol.user.mydata.dto;

import com.sol.user.holding.entity.Holding;

import java.math.BigDecimal;

public record MydataHoldingResponse(
        Long holdingId,
        Long accountId,
        Long productId,
        BigDecimal evaluationAmount,
        BigDecimal quantity,
        String avgPurchasePrice,
        String unrealizedGainLoss,
        Boolean frozen
) {
    public static MydataHoldingResponse from(Holding holding) {
        return new MydataHoldingResponse(
                holding.getHoldingId(),
                holding.getAccount().getAccountId(),
                holding.getProductId(),
                holding.getEvaluationAmount(),
                holding.getQuantity(),
                holding.getAvgPurchasePrice(),
                holding.getUnrealizedGainLoss(),
                holding.getFrozen()
        );
    }
}
