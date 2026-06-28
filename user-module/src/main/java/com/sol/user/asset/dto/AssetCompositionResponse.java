package com.sol.user.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AssetCompositionResponse {
    private BigDecimal totalAsset;
    private BigDecimal totalDebt;
    private BigDecimal netWorth;
    private List<AssetAllocationItem> allocation;
    private List<AssetGroupItem> groups;

    @Getter
    @Builder
    public static class AssetGroupItem {
        private String category;
        private String label;
        private BigDecimal totalAmount;
        private List<AssetAccountItem> accounts;
    }

    @Getter
    @Builder
    public static class AssetAccountItem {
        private Long accountId;
        private String institutionName;
        private String accountType;
        private BigDecimal balance;
        private BigDecimal interestRate;
        private LocalDate maturityDate;
        private List<AssetHoldingItem> holdings;
    }

    @Getter
    @Builder
    public static class AssetHoldingItem {
        private String productName;
        private String tickerCode;
        private BigDecimal quantity;
        private BigDecimal evaluationAmount;
    }
}
