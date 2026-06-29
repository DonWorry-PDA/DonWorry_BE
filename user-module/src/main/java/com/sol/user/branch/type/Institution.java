package com.sol.user.branch.type;

/** 영업점 운영 기관. 화면(은행/증권)별로 분리되어 조회된다. */
public enum Institution {
    SHINHAN_BANK("신한은행"),
    SHINHAN_SECURITIES("신한투자증권");

    private final String label;

    Institution(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
