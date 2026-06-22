package com.sol.user.stability.type;

public enum LifeStabilityGrade {
    STABLE("\uC548\uC815"),
    NEED_COMPLEMENT("\uBCF4\uC644 \uD544\uC694"),
    NEED_IMPROVEMENT("\uAC1C\uC120 \uD544\uC694");

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
        if (score >= 50) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }
}
