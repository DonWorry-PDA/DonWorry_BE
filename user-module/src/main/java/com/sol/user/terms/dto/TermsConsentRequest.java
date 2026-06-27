package com.sol.user.terms.dto;

import jakarta.validation.constraints.NotNull;

public record TermsConsentRequest(
        @NotNull Boolean agreed
) {}
