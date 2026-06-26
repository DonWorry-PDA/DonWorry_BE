package com.sol.user.asset.infra.rest;

import java.util.List;

public record DepositDetailApiResponse(
        String code,
        String message,
        List<DepositDetailItem> data
) {}
