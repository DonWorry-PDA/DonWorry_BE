package com.sol.user.consultation.type;

/** 상담 방식 — 영업점 대면 또는 전화. */
public enum ConsultMethod {
    FACE_TO_FACE("영업점 대면 상담"),
    PHONE("전화 상담");

    private final String label;

    ConsultMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
