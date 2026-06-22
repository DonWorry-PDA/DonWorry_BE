package com.sol.user.stability.message;

import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.type.RecommendedPlanType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class LifeStabilityMessageGenerator {

    public String generateSummary(LifeStabilityCalculatedResult result) {
        return switch (result.getGrade()) {
            case STABLE -> "\uD604\uC7AC \uC0DD\uD65C\uBE44\uB97C \uC548\uC815\uC801\uC73C\uB85C \uCDA9\uB2F9\uD560 \uC218 \uC788\uB294 \uC0C1\uD0DC\uC608\uC694.";
            case NEED_COMPLEMENT -> "\uD604\uC7AC \uC0DD\uD65C\uBE44\uB294 \uC77C\uBD80 \uCDA9\uB2F9\uB418\uACE0 \uC788\uC9C0\uB9CC, \uBCF4\uC644\uC774 \uD544\uC694\uD574\uC694.";
            case NEED_IMPROVEMENT -> "\uD604\uC7AC \uD604\uAE08\uD750\uB984\uB9CC\uC73C\uB85C\uB294 \uC0DD\uD65C\uBE44\uAC00 \uBD80\uC871\uD574\uC694. \uC548\uC815\uC801\uC778 \uD604\uAE08\uD750\uB984 \uD655\uBCF4\uAC00 \uC6B0\uC120\uC774\uC5D0\uC694.";
        };
    }

    public List<String> generateImprovementMessages(LifeStabilityCalculatedResult result) {
        List<String> messages = new ArrayList<>();

        if (result.getLiquidityMonths().compareTo(BigDecimal.valueOf(6)) < 0) {
            messages.add("\uC720\uB3D9\uC131 \uC790\uC0B0\uC774 \uCDA9\uBD84\uD558\uC9C0 \uC54A\uC744 \uC218 \uC788\uC5B4\uC694. \uCD5C\uC18C 6\uAC1C\uC6D4\uCE58 \uD544\uC218\uC9C0\uCD9C\uC744 \uBA3C\uC800 \uD655\uBCF4\uD558\uB294 \uAC83\uC774 \uC88B\uC544\uC694.");
        }

        if (result.getMedicalPreparednessMonths().compareTo(BigDecimal.valueOf(12)) < 0) {
            messages.add("\uC608\uC0C1 \uC758\uB8CC\uBE44\uC5D0 \uB300\uBE44\uD55C \uC900\uBE44\uC790\uAE08\uC774 \uBD80\uC871\uD560 \uC218 \uC788\uC5B4\uC694.");
        }

        if (result.getDebtBurdenRate().compareTo(BigDecimal.valueOf(20)) > 0) {
            messages.add("\uC6D4 \uC18C\uB4DD \uB300\uBE44 \uB300\uCD9C \uC0C1\uD658 \uBD80\uB2F4\uC774 \uB192\uC740 \uD3B8\uC774\uC5D0\uC694.");
        }

        if (result.getRiskAssetDependencyRate().compareTo(BigDecimal.valueOf(20)) > 0) {
            messages.add("\uC0DD\uD65C\uBE44 \uC77C\uBD80\uB97C \uC704\uD5D8\uC790\uC0B0\uC5D0 \uC758\uC874\uD560 \uAC00\uB2A5\uC131\uC774 \uC788\uC5B4 \uC548\uC815\uC801\uC778 \uD604\uAE08\uD750\uB984 \uD655\uBCF4\uAC00 \uD544\uC694\uD574\uC694.");
        }

        if (messages.isEmpty()) {
            messages.add("\uD604\uC7AC \uC0C1\uD0DC\uC5D0\uC11C\uB294 \uD070 \uC704\uD5D8 \uC694\uC778\uC774 \uB450\uB4DC\uB7EC\uC9C0\uC9C0 \uC54A\uC544\uC694.");
        }

        return messages;
    }

    public String generateGuardrailReason(LifeStabilityCalculatedResult result) {
        if (result.getRecommendedPlanType() == RecommendedPlanType.GROWTH_EXTRA_ASSET) {
            return "\uC0DD\uD65C \uC548\uC815\uB3C4\uAC00 \uB9E4\uC6B0 \uC591\uD638\uD574 \uC5EC\uC720\uC790\uAE08 \uC131\uC7A5\uD615\uAE4C\uC9C0 \uAC80\uD1A0\uD560 \uC218 \uC788\uC5B4\uC694.";
        }

        if (result.getRecommendedPlanType() == RecommendedPlanType.BALANCED_INCOME) {
            return "\uC0DD\uD65C \uC548\uC815\uB3C4\uAC00 \uC591\uD638\uD574 \uADE0\uD615 \uC6D4\uAE09\uD615\uC744 \uC6B0\uC120 \uAC80\uD1A0\uD560 \uC218 \uC788\uC5B4\uC694.";
        }

        return "\uC0DD\uD65C \uC548\uC815\uB3C4 \uBCF4\uC644\uC774 \uD544\uC694\uD558\uC5EC \uC548\uC815\uC801\uC778 \uD604\uAE08\uD750\uB984 \uC124\uACC4\uC548\uC744 \uC6B0\uC120 \uAC80\uD1A0\uD558\uB294 \uAC83\uC774 \uC88B\uC544\uC694.";
    }
}
