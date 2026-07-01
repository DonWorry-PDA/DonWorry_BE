package com.sol.user.portfolio.dto;

import java.time.LocalDateTime;

public record SavePlanResponse(
        Long id,
        LocalDateTime savedAt
) {}
