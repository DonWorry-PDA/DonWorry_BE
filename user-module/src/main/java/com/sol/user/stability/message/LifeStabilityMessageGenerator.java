package com.sol.user.stability.message;

import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.type.LifeStabilityIndicatorStatus;
import com.sol.user.stability.type.RecommendedPlanType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LifeStabilityMessageGenerator {

    public String generateSummary(LifeStabilityCalculatedResult result) {
        return switch (result.getGrade()) {
            case STABLE -> "현재 생활비를 안정적으로 충당할 수 있는 상태예요.";
            case NEED_COMPLEMENT -> "현재 생활비는 일부 충당되고 있지만, 보완이 필요해요.";
            case NEED_IMPROVEMENT -> "현재 현금흐름만으로는 생활비가 부족해요. 안정적인 현금흐름 확보가 우선이에요.";
        };
    }

    /**
     * 개선 제안 — 화면 지표 배지(indicators)와 동일한 상태 판정({@link LifeStabilityIndicatorStatus})을 사용해
     * '안정'이 아닌 지표마다 대응 메시지를 생성한다. 모든 지표가 안정일 때만 위험 없음 안내.
     */
    public List<String> generateImprovementMessages(LifeStabilityCalculatedResult result) {
        List<String> messages = new ArrayList<>();

        if (LifeStabilityIndicatorStatus.ofCashflowCoverage(result.getCashflowCoverageRate()) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("월 확보 수입이 목표 생활비에 미치지 못해요. 안정적인 현금흐름 확보가 필요해요.");
        }

        if (LifeStabilityIndicatorStatus.ofEssentialExpense(result.getEssentialExpenseRate()) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("수입 대비 필수지출 비중이 높은 편이에요. 고정 지출을 점검해 보세요.");
        }

        if (LifeStabilityIndicatorStatus.ofMonths(result.getMedicalPreparednessMonths(), LifeStabilityIndicatorStatus.MEDICAL_GOOD_MONTHS) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("예상 의료비에 대비한 준비자금이 부족할 수 있어요.");
        }

        if (LifeStabilityIndicatorStatus.ofMonths(result.getLiquidityMonths(), LifeStabilityIndicatorStatus.LIQUIDITY_GOOD_MONTHS) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("유동성 자산이 충분하지 않을 수 있어요. 최소 6개월치 필수지출을 먼저 확보하는 것이 좋아요.");
        }

        if (LifeStabilityIndicatorStatus.ofDebtBurden(result.getDebtBurdenRate()) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("월 소득 대비 대출 상환 부담이 높은 편이에요.");
        }

        if (LifeStabilityIndicatorStatus.ofRiskAssetDependency(result.getRiskAssetDependencyRate()) != LifeStabilityIndicatorStatus.STABLE) {
            messages.add("생활비 일부를 위험자산에 의존할 가능성이 있어 안정적인 현금흐름 확보가 필요해요.");
        }

        if (messages.isEmpty()) {
            messages.add("현재 상태에서는 큰 위험 요인이 두드러지지 않아요.");
        }

        return messages;
    }

    public String generateGuardrailReason(LifeStabilityCalculatedResult result) {
        if (result.getRecommendedPlanType() == RecommendedPlanType.GROWTH_EXTRA_ASSET) {
            return "생활 안정도가 매우 양호해 여유자금 성장형까지 검토할 수 있어요.";
        }

        if (result.getRecommendedPlanType() == RecommendedPlanType.BALANCED_INCOME) {
            return "생활 안정도가 양호해 균형 월급형을 우선 검토할 수 있어요.";
        }

        return "생활 안정도 보완이 필요하여 안정적인 현금흐름 설계안을 우선 검토하는 것이 좋아요.";
    }
}
