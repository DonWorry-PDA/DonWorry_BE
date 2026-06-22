package com.sol.user.portfolio.service;

import com.sol.user.portfolio.calculator.AlphaCoverageCalculator;
import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.calculator.PortfolioAllocationCalculator;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PortfolioRecommendationServiceTest {

    private final EtfPoolProvider etfPoolProvider = mock(EtfPoolProvider.class);

    private final PortfolioRecommendationService service = new PortfolioRecommendationService(
            new OperationGradeCalculator(),
            new PortfolioAllocationCalculator(),
            new AlphaCoverageCalculator(),
            etfPoolProvider
    );

    @Test
    void STEP1부터6까지_조립되어_위험중립형_2안_추천이_나온다() {
        given(etfPoolProvider.getPool()).willReturn(pool());

        RecommendationResponse response = service.recommend(1L);

        // mock 입력(위험중립형) → NORMAL, 2안(안정·유동성, 균형안 미제공)
        assertThat(response.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(response.getPlans()).extracting(PlanResponse::getType)
                .containsExactly(PlanType.STABLE, PlanType.LIQUIDITY);

        // 각 안에 배분+수령이 병합되어 있음
        assertThat(response.getPlans()).allSatisfy(plan -> {
            assertThat(plan.getMonthlyIncome()).isPositive();
            assertThat(plan.getAlphaCoverageRate()).isNotNull(); // NORMAL이라 충족률 존재
            assertThat(plan.getHoldings()).isNotEmpty();
        });

        // Q3 트레이드오프 표 + 기준 라벨
        assertThat(response.getQ3Scenarios()).hasSize(3);
        assertThat(response.getQ3ReferenceLabel()).isEqualTo("안정안 기준 예시");
        assertThat(response.getBand()).isNotNull();
    }

    private List<EtfInfo> pool() {
        return List.of(
                new EtfInfo("446720", "SOL 미국배당다우존스", 3,
                        new BigDecimal("3.50"), "MONTHLY", BucketRole.RISK, CurrencyExposure.UNHEDGED),
                new EtfInfo("452360", "SOL 미국배당다우존스(H)", 2,
                        new BigDecimal("3.40"), "MONTHLY", BucketRole.RISK, CurrencyExposure.HEDGED),
                new EtfInfo("476030", "SOL 미국나스닥100", 2,
                        new BigDecimal("1.20"), "QUARTERLY", BucketRole.RISK, CurrencyExposure.UNHEDGED)
        );
    }
}
