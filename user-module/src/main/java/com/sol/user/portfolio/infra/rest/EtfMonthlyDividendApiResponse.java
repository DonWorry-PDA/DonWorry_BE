package com.sol.user.portfolio.infra.rest;

import java.util.List;

public record EtfMonthlyDividendApiResponse(
        String code,
        String message,
        List<EtfMonthlyDividendItem> data
) {
}
