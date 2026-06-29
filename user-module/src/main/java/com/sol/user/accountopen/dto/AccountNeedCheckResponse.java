package com.sol.user.accountopen.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountNeedCheckResponse {

    private boolean needsAccount;
    private String reason;

    public static AccountNeedCheckResponse noNeedDonWorry() {
        return AccountNeedCheckResponse.builder()
                .needsAccount(false)
                .reason("HAS_DON_WORRY")
                .build();
    }

    public static AccountNeedCheckResponse noNeedShinhanBoth() {
        return AccountNeedCheckResponse.builder()
                .needsAccount(false)
                .reason("HAS_SHINHAN_BOTH")
                .build();
    }

    public static AccountNeedCheckResponse needsAccount() {
        return AccountNeedCheckResponse.builder()
                .needsAccount(true)
                .reason("NEEDS_ACCOUNT")
                .build();
    }
}
