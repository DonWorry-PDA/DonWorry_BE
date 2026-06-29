package com.sol.user.portfolio.infra.rest;

import java.util.Map;

public record StockPriceApiResponse(String code, String message, Map<Long, Long> data) {
}
