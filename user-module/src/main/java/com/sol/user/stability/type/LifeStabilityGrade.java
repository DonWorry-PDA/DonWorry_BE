package com.sol.user.stability.type;

public enum LifeStabilityGrade {
    STABLE("\uC548\uC815"),
    NEED_IMPROVEMENT("\uBCF4\uC644 \uD544\uC694"),
    CAUTION("\uC8FC\uC758"),
    RISK("\uC704\uD5D8");

    private final String label;

    LifeStabilityGrade(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static LifeStabilityGrade fromScore(int score) {
        if (score >= 80) {
            return STABLE;
        }
        if (score >= 60) {
            return NEED_IMPROVEMENT;
        }
        if (score >= 40) {
            return CAUTION;
        }
        return RISK;
    }
}
