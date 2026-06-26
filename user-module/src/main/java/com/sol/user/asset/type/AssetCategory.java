package com.sol.user.asset.type;

/**
 * 자산관리 허브 화면의 자산 분포 표시 카테고리.
 * 예수금만 계약: 계좌 잔액(예수금/현금)은 accountType으로, 보유종목 평가액은 productType으로 매핑한다.
 * (BROKERAGE 계좌 잔액 = 예수금이라 현금성(예금)으로 보고, ETF/주식 가치는 보유종목에서 나온다.)
 */
public enum AssetCategory {
    PENSION("연금"),
    DEPOSIT("예금"),
    ETF("ETF"),
    STOCK("주식"),
    ETC("기타");

    private final String label;

    AssetCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AssetCategory fromAccountType(String accountType) {
        if (accountType == null) {
            return ETC;
        }
        return switch (accountType) {
            case "IRP", "PENSION_SAVING" -> PENSION;
            case "CMA", "DEPOSIT" -> DEPOSIT;
            case "BROKERAGE" -> ETF;
            default -> ETC;
        };
    }

    /** 보유종목 평가액 표시 카테고리. ETF는 ETF, 개별주식은 주식, 그 외(FUND·BOND 등)는 기타. */
    public static AssetCategory fromProductType(String productType) {
        if (productType == null) {
            return ETC;
        }
        return switch (productType) {
            case "ETF" -> ETF;
            case "STOCK" -> STOCK;
            default -> ETC;
        };
    }
}
