package com.sol.user.accountopen.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IdentityResponse {
    private String name;
    private String idNumberMasked;
    private String phone;
}
