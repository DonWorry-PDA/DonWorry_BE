package com.sol.user.portfolio.infra.rest;

import java.util.List;

public record ProductBatchApiResponse(
        String code,
        String message,
        List<ProductBatchItem> data
) {
}
