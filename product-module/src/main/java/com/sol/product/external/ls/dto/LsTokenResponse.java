package com.sol.product.external.ls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LsTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        String scope
) {}
