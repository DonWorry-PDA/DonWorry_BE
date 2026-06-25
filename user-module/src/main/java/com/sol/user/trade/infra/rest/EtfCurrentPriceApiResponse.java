package com.sol.user.trade.infra.rest;

public record EtfCurrentPriceApiResponse(
        String code,
        String message,
        Long data
) {
}
