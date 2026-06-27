package com.sol.user.stability.message;

import com.sol.user.stability.dto.LifeStabilityCalculatedResult;
import com.sol.user.stability.type.LifeStabilityGrade;
import com.sol.user.stability.type.RecommendedPlanType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LifeStabilityMessageGeneratorTest {

    private static final String FALLBACK = "현재 상태에서는 큰 위험 요인이 두드러지지 않아요.";
    private static final String CASHFLOW_MSG = "월 확보 수입이 목표 생활비에 미치지 못해요. 안정적인 현금흐름 확보가 필요해요.";
    private static final String EXPENSE_MSG = "수입 대비 필수지출 비중이 높은 편이에요. 고정 지출을 점검해 보세요.";
    private static final String MEDICAL_MSG = "예상 의료비에 대비한 준비자금이 부족할 수 있어요.";

    private final LifeStabilityMessageGenerator generator = new LifeStabilityMessageGenerator();

    private LifeStabilityCalculatedResult.LifeStabilityCalculatedResultBuilder allStable() {
        // 모든 지표 '안정' 기준을 만족하는 베이스
        return LifeStabilityCalculatedResult.builder()
                .totalScore(100)
                .grade(LifeStabilityGrade.STABLE)
                .cashflowCoverageRate(BigDecimal.valueOf(120))   // >= 100 안정
                .essentialExpenseRate(BigDecimal.valueOf(40))    // <= 50 안정
                .medicalPreparednessMonths(BigDecimal.valueOf(30)) // >= 24 안정
                .liquidityMonths(BigDecimal.valueOf(12))         // >= 6 안정
                .debtBurdenRate(BigDecimal.valueOf(5))           // <= 30 안정
                .riskAssetDependencyRate(BigDecimal.ZERO)        // <= 0 안정
                .growthPlanAllowed(true)
                .recommendedPlanType(RecommendedPlanType.GROWTH_EXTRA_ASSET);
    }

    @Test
    void allIndicatorsStableReturnsOnlyFallback() {
        List<String> messages = generator.generateImprovementMessages(allStable().build());

        assertThat(messages).containsExactly(FALLBACK);
    }

    @Test
    void includesCashflowAndExpenseMessagesWhenThoseIndicatorsAreNotStable() {
        // #201 재현: 충당률 보완필요·필수지출 개선필요·의료대비 보완필요인데
        // 기존 로직은 이 셋을 무시해 fallback("위험 없음")이 떴다.
        LifeStabilityCalculatedResult result = allStable()
                .grade(LifeStabilityGrade.NEED_COMPLEMENT)
                .cashflowCoverageRate(BigDecimal.valueOf(86.53))   // < 100 → 보완 필요
                .essentialExpenseRate(BigDecimal.valueOf(78.75))   // > 70 → 개선 필요
                .medicalPreparednessMonths(BigDecimal.valueOf(14)) // < 24 → 보완 필요
                .liquidityMonths(BigDecimal.valueOf(19.88))        // 안정
                .debtBurdenRate(BigDecimal.valueOf(20))            // 안정(<= 30)
                .riskAssetDependencyRate(BigDecimal.ZERO)          // 안정
                .build();

        List<String> messages = generator.generateImprovementMessages(result);

        assertThat(messages)
                .containsExactly(CASHFLOW_MSG, EXPENSE_MSG, MEDICAL_MSG)
                .doesNotContain(FALLBACK);
    }

    @Test
    void nullMetricsDoNotThrowAndFallBackToNoRisk() {
        // StabilityScore 지표 컬럼은 nullable — null 행이 와도 NPE 없이 fallback이어야 한다.
        LifeStabilityCalculatedResult result = LifeStabilityCalculatedResult.builder()
                .totalScore(0)
                .grade(LifeStabilityGrade.STABLE)
                .build();

        List<String> messages = generator.generateImprovementMessages(result);

        assertThat(messages).containsExactly(FALLBACK);
    }

    @Test
    void cashflowMessageAppearsWheneverCashflowIndicatorIsNotStable() {
        // 다른 지표는 모두 안정이고 충당률만 미달이어도 메시지가 나와야 한다(기존엔 누락).
        LifeStabilityCalculatedResult result = allStable()
                .cashflowCoverageRate(BigDecimal.valueOf(95))
                .build();

        List<String> messages = generator.generateImprovementMessages(result);

        assertThat(messages).containsExactly(CASHFLOW_MSG);
    }
}
