package com.sol.user.stability.type;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum LifeStabilityIndicatorStatus {
    STABLE("안정"),
    NEED_COMPLEMENT("보완 필요"),
    NEED_IMPROVEMENT("개선 필요");

    /** 의료비 대비력 '안정' 기준 개월(이상이면 안정). */
    public static final BigDecimal MEDICAL_GOOD_MONTHS = BigDecimal.valueOf(24);
    /** 유동성 '안정' 기준 개월(이상이면 안정). */
    public static final BigDecimal LIQUIDITY_GOOD_MONTHS = BigDecimal.valueOf(6);

    private final String label;

    LifeStabilityIndicatorStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 지표 상태 판정 — 화면 배지(indicators)와 개선 제안(improvementMessages)이
     * 동일 기준을 쓰도록 단일 출처로 모은다.
     */
    public static LifeStabilityIndicatorStatus ofCashflowCoverage(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }

    public static LifeStabilityIndicatorStatus ofEssentialExpense(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }

    public static LifeStabilityIndicatorStatus ofMonths(BigDecimal months, BigDecimal goodThreshold) {
        if (months.compareTo(goodThreshold) >= 0) {
            return STABLE;
        }
        BigDecimal normalThreshold = goodThreshold.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (months.compareTo(normalThreshold) >= 0) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }

    public static LifeStabilityIndicatorStatus ofDebtBurden(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(40)) <= 0) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }

    public static LifeStabilityIndicatorStatus ofRiskAssetDependency(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return STABLE;
        }
        if (rate.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return NEED_COMPLEMENT;
        }
        return NEED_IMPROVEMENT;
    }
}
