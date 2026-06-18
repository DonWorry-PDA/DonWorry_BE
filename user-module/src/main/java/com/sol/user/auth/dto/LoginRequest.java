package com.sol.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotNull Long userId,
        @NotBlank String pin
) {}
