package com.sol.user.terms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

public record TermsStatusResponse(
        TermConsent thirdParty,
        TermConsent marketing
) {
    public record TermConsent(
            boolean agreed,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @JsonFormat(pattern = "yyyy.MM.dd")
            LocalDate agreedAt
    ) {}
}
