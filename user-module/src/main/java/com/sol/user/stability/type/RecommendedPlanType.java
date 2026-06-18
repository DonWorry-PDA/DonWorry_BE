package com.sol.user.stability.type;

public enum RecommendedPlanType {
    STABLE_INCOME("\uC548\uC815 \uC6D4\uAE09\uD615"),
    BALANCED_INCOME("\uADE0\uD615 \uC6D4\uAE09\uD615"),
    GROWTH_EXTRA_ASSET("\uC5EC\uC720\uC790\uAE08 \uC131\uC7A5\uD615");

    private final String label;

    RecommendedPlanType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
