package com.sol.user.consultation.type;

/** 상담 방식. */
public enum ConsultMethod {
    FACE_TO_FACE("영업점 대면 상담"),
    ONLINE("비대면 상담");

    private final String label;

    ConsultMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
