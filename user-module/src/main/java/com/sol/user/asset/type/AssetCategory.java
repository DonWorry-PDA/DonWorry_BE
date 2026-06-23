package com.sol.user.asset.type;

/**
 * 자산관리 허브 화면의 자산 분포 표시 카테고리.
 * 계좌의 accountType 코드를 화면 표시용 4개 버킷(+기타)으로 매핑한다.
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
}
