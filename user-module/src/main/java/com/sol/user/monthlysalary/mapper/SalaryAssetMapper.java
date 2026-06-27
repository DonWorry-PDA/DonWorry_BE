package com.sol.user.monthlysalary.mapper;

import com.sol.user.account.entity.Account;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.monthlysalary.dto.AssetItemDto;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    public AssetItemDto toHoldingItem(HoldingWithProduct holding, ProductBatchItem product, Set<String> excludedKeys) {
        // 제외키는 productId 기반 — 재동기화로 holding이 삭제·재삽입돼 holdingId가 바뀌어도 제외 유지.
        String assetKey = createHoldingAssetKey(holding.getProductId());
        String productName = product != null ? product.productName() : null;
        String productType = product != null ? product.productType() : null;

        return AssetItemDto.builder()
                .assetKey(assetKey)
                .name(productName)
                .description(resolveHoldingDescription(productType))
                .amount(holding.getEvaluationAmount())
                .excluded(excludedKeys.contains(assetKey))
                .build();
    }

    public String createAccountAssetKey(Long accountId) {
        return ACCOUNT_ASSET_KEY_PREFIX + accountId;
    }

    /** 보유종목 제외키는 productId 기반(holdingId는 재동기화 시 재발급되어 불안정). */
    public String createHoldingAssetKey(Long productId) {
        return HOLDING_ASSET_KEY_PREFIX + productId;
    }

    /** 제외목록(assetKey)에서 계좌 ID만 추출. 월급 집계 필터({@code AssetAggregator})가 키 포맷을 모르게 ID로 넘기기 위함. */
    public Set<Long> extractAccountIds(Set<String> assetKeys) {
        return extractIds(assetKeys, ACCOUNT_ASSET_KEY_PREFIX);
    }

    /** 제외목록(assetKey)에서 보유종목 productId만 추출. */
    public Set<Long> extractExcludedProductIds(Set<String> assetKeys) {
        return extractIds(assetKeys, HOLDING_ASSET_KEY_PREFIX);
    }

    private Set<Long> extractIds(Set<String> assetKeys, String prefix) {
        if (assetKeys == null || assetKeys.isEmpty()) {
            return Set.of();
        }
        return assetKeys.stream()
                .filter(key -> key != null && key.startsWith(prefix))
                .map(key -> {
                    try {
                        return Long.parseLong(key.substring(prefix.length()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public String resolveAccountTypeLabel(String accountType) {
        if (accountType == null) {
            return "기타";
        }
        return switch (accountType) {
            case "IRP" -> "IRP";
            case "PENSION_SAVING" -> "연금저축";
            case "DEPOSIT" -> "예금";
            case "CMA" -> "CMA";
            case "BROKERAGE" -> "증권";
            default -> accountType;
        };
    }

    private String resolveAccountName(String accountType) {
        if (accountType == null) {
            return null;
        }
        return switch (accountType) {
            case "IRP" -> "IRP";
            case "PENSION_SAVING" -> "연금저축";
            case "DEPOSIT" -> "정기예금";
            case "CMA" -> "CMA";
            case "BROKERAGE" -> "예수금";
            default -> accountType;
        };
    }

    private String resolveAccountDescription(String accountType) {
        if (accountType == null) {
            return null;
        }
        return switch (accountType) {
            case "IRP" -> "연금 계좌";
            case "PENSION_SAVING" -> "세액공제 계좌";
            case "DEPOSIT" -> "예금 이자 수령 자산";
            case "CMA" -> "수시 입출금 자산";
            default -> null;
        };
    }

    private String resolveHoldingDescription(String productType) {
        if (productType == null) {
            return null;
        }
        return switch (productType) {
            case "ETF" -> "분배금 수령 가능 상품";
            case "FUND" -> "펀드 분배금 가능 상품";
            case "BOND" -> "채권 이자 수령 가능 상품";
            default -> null;
        };
    }
}
