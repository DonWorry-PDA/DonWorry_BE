package com.sol.user.stability.type;

public enum LifeStabilityIndicatorStatus {
    GOOD("\uC88B\uC74C"),
    NORMAL("\uBCF4\uD1B5"),
    NEED_CHECK("\uD655\uC778 \uD544\uC694"),
    WEAK("\uBD80\uC871");

    private final String label;

    LifeStabilityIndicatorStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
