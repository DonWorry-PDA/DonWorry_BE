package com.sol.user.asset.infra.rest;

import java.util.List;

public record DepositDetailIdsResponse(
        String code,
        String message,
        List<Long> data
) {}
