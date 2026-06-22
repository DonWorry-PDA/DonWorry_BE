package com.sol.user.stability.type;

public enum LifeStabilityIndicatorStatus {
    STABLE("\uC548\uC815"),
    NEED_COMPLEMENT("\uBCF4\uC644 \uD544\uC694"),
    NEED_IMPROVEMENT("\uAC1C\uC120 \uD544\uC694");

    private final String label;

    LifeStabilityIndicatorStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
