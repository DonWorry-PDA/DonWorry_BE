package com.sol.user.monthlysalary.mapper;

import com.sol.user.account.entity.Account;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.monthlysalary.dto.AssetItemDto;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SalaryAssetMapper {

    private static final String ACCOUNT_ASSET_KEY_PREFIX = "ACCOUNT_";
    private static final String HOLDING_ASSET_KEY_PREFIX = "HOLDING_";

    public AssetItemDto toAccountItem(Account account, Set<String> excludedKeys) {
        String assetKey = createAccountAssetKey(account.getAccountId());

        return AssetItemDto.builder()
                .assetKey(assetKey)
                .name(resolveAccountName(account.getAccountType()))
                .description(resolveAccountDescription(account.getAccountType()))
                .amount(account.getDepositBalance())
                .excluded(excludedKeys.contains(assetKey))
                .build();
    }

    public AssetItemDto toHoldingItem(HoldingWithProduct holding, Set<String> excludedKeys) {
        String assetKey = createHoldingAssetKey(holding.getHoldingId());

        return AssetItemDto.builder()
                .assetKey(assetKey)
                .name(holding.getProductName())
                .description(resolveHoldingDescription(holding.getProductType()))
                .amount(holding.getEvaluationAmount())
                .excluded(excludedKeys.contains(assetKey))
                .build();
    }

    public String createAccountAssetKey(Long accountId) {
        return ACCOUNT_ASSET_KEY_PREFIX + accountId;
    }

    public String createHoldingAssetKey(Long holdingId) {
        return HOLDING_ASSET_KEY_PREFIX + holdingId;
    }

    private String resolveAccountName(String accountType) {
        return switch (accountType) {
            case "IRP" -> "IRP";
            case "PENSION_SAVING" -> "연금저축";
            case "DEPOSIT" -> "정기예금";
            default -> accountType;
        };
    }

    private String resolveAccountDescription(String accountType) {
        return switch (accountType) {
            case "IRP" -> "연금 계좌";
            case "PENSION_SAVING" -> "세액공제 계좌";
            case "DEPOSIT" -> "예금 이자 수령 자산";
            default -> null;
        };
    }

    private String resolveHoldingDescription(String productType) {
        return switch (productType) {
            case "ETF" -> "분배금 수령 가능 상품";
            case "FUND" -> "펀드 분배금 가능 상품";
            case "BOND" -> "채권 이자 수령 가능 상품";
            case "STOCK" -> "배당 수령 가능 상품";
            default -> null;
        };
    }
}
