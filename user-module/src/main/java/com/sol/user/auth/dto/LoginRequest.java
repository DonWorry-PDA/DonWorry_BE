package com.sol.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull Long userId,
        @NotBlank @Size(min = 4, max = 20) String pin
) {}
