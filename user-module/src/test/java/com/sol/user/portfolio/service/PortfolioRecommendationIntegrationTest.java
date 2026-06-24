package com.sol.user.portfolio.service;

import com.sol.user.asset.service.AssetMockService;
import com.sol.user.asset.type.MockType;
import com.sol.user.onboarding.dto.OnboardingRequest;
import com.sol.user.onboarding.service.OnboardingService;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.RecommendationTrack;
import com.sol.user.survey.dto.SurveySaveRequest;
import com.sol.user.survey.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 추천 풀 통합 — 실제 서비스 체인(온보딩→설문→마이데이터 목업 시드→추천)을 H2에 태워
 * DB 행 → OperationGradeInputAssembler → STEP1~6 배선이 실제로 동작하는지 검증한다.
 * product 풀 조회(REST)만 목 대체(user-module 단독 컨텍스트라 product-module 부재).
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class PortfolioRecommendationIntegrationTest {

    @Autowired private OnboardingService onboardingService;
    @Autowired private SurveyService surveyService;
    @Autowired private AssetMockService assetMockService;
    @Autowired private PortfolioRecommendationService recommendationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private EtfPoolProvider etfPoolProvider;

    @BeforeEach
    void stubPool() {
        given(etfPoolProvider.getPool()).willReturn(fullPool());
    }

    @Test
    void 안정형_시드유저는_어떤_안에도_위험ETF가_배분되지_않는다() {
        // userId 3 → STABLE 시나리오 + STABLE 성향 시드 → #118 권유가능등급 필터로 위험 차단
        prepareUser(3L, 68, MockType.STABLE, BigDecimal.valueOf(3_000_000));

        RecommendationResponse response = recommendationService.recommend(3L);
        print("STABLE(user3)", response);

        assertThat(response.getPlans()).isNotEmpty();
        assertThat(response.getPlans()).allSatisfy(plan -> {
            assertThat(plan.getRiskTarget()).isEqualByComparingTo("0");
            assertThat(plan.getHoldings()).noneMatch(h -> h.role() == BucketRole.RISK);
        });
    }

    @Test
    void 적극형_시드유저는_위험ETF가_배분된다() {
        // userId 1 → NEED_IMPROVEMENT 시나리오 + ACTIVE 성향 시드 (자산 규모에 맞춘 생활비로 NORMAL 진입)
        prepareUser(1L, 60, MockType.NEED_IMPROVEMENT, BigDecimal.valueOf(1_000_000));

        RecommendationResponse response = recommendationService.recommend(1L);
        print("ACTIVE(user1)", response);

        assertThat(response.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(response.getPlans()).anySatisfy(plan ->
                assertThat(plan.getHoldings()).anyMatch(h -> h.role() == BucketRole.RISK));
    }

    @Test
    void 위험중립_시드유저는_위험ETF가_배분된다() {
        // userId 2 → NEED_COMPLEMENT 시나리오 + NEUTRAL 성향 시드
        prepareUser(2L, 64, MockType.NEED_COMPLEMENT, BigDecimal.valueOf(1_500_000));

        RecommendationResponse response = recommendationService.recommend(2L);
        print("NEUTRAL(user2)", response);

        assertThat(response.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(response.getPlans()).anySatisfy(plan ->
                assertThat(plan.getHoldings()).anyMatch(h -> h.role() == BucketRole.RISK));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void prepareUser(Long userId, int age, MockType mockType, BigDecimal targetLivingCost) {
        jdbcTemplate.update("INSERT INTO users (user_id, onboarding_completed) VALUES (?, ?)", userId, false);
        onboardingService.complete(userId, new OnboardingRequest(
                age, true, true, targetLivingCost, BigDecimal.valueOf(350_000)));
        surveyService.save(userId, survey(2, 1, 1));
        assetMockService.seed(userId, mockType);
    }

    private SurveySaveRequest survey(int q1, int q2, int q3) {
        SurveySaveRequest request = new SurveySaveRequest();
        ReflectionTestUtils.setField(request, "q1", q1);
        ReflectionTestUtils.setField(request, "q2", q2);
        ReflectionTestUtils.setField(request, "q3", q3);
        return request;
    }

    private void print(String label, RecommendationResponse response) {
        System.out.printf("INTEG| %-14s track=%s plans=%d%n", label, response.getTrack(), response.getPlans().size());
        for (PlanResponse plan : response.getPlans()) {
            String risk = plan.getHoldings().stream()
                    .filter(h -> h.role() == BucketRole.RISK)
                    .map(h -> h.ticker() + "(" + h.amount().longValue() + ")")
                    .reduce((a, b) -> a + "," + b).orElse("없음");
            System.out.printf("INTEG|   %-9s riskTarget=%,d 위험=[%s]%n",
                    plan.getType(), plan.getRiskTarget().longValue(), risk);
        }
    }

    /** product REST 풀 대체 — 코어·안전·목업 holding 시드에 쓰이는 ticker를 모두 포함. */
    private List<EtfInfo> fullPool() {
        List<String> tickers = List.of(
                "433330", "476030", "292500", "446720", "438560", "452360", "436140", "497880");
        List<EtfInfo> pool = new ArrayList<>();
        long productId = 1000L;
        for (String ticker : tickers) {
            pool.add(new EtfInfo(
                    ++productId,
                    ticker,
                    "SOL " + ticker,
                    PortfolioConstants.RISK_GRADE.getOrDefault(ticker, 3),
                    new BigDecimal("3.00"),
                    "MONTHLY",
                    PortfolioConstants.roleOf(ticker),
                    PortfolioConstants.currencyOf(ticker)
            ));
        }
        return pool;
    }
}
