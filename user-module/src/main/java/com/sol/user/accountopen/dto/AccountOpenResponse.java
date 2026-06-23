package com.sol.user.accountopen.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountOpenResponse {
    private String accountNumber;
    private String openedAt;
}
