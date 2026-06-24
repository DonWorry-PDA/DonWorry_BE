package com.sol.user.mydata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InstitutionConnectRequest(
        @NotNull @NotEmpty List<String> institutionIds
) {}
