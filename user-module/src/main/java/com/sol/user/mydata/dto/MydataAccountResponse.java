package com.sol.user.mydata.dto;

import com.sol.user.account.entity.Account;

import java.math.BigDecimal;

public record MydataAccountResponse(
        Long accountId,
        String accountType,
        String institutionName,
        String accountNumber,
        BigDecimal depositBalance,
        Boolean existingAccount
) {
    public static MydataAccountResponse from(Account account) {
        return new MydataAccountResponse(
                account.getAccountId(),
                account.getAccountType(),
                account.getInstitutionName(),
                account.getAccountNumber(),
                account.getDepositBalance(),
                account.getExistingAccount()
        );
    }
}
