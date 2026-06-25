package com.sol.user.consultation.type;

/** 상담 유형 — FE ConsultType('pb' | 'insurance')과 1:1. */
public enum ConsultType {
    PB("자산 설계 상담"),
    INSURANCE("보험 점검 상담");

    private final String defaultTitle;

    ConsultType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }
}
