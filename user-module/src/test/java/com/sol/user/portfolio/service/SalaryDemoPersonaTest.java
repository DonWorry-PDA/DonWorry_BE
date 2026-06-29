package com.sol.user.portfolio.service;

import com.sol.user.asset.service.AssetMockService;
import com.sol.user.asset.type.MockType;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.dto.PlanResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.portfolio.type.RecommendationTrack;
import com.sol.user.survey.dto.SurveySaveRequest;
import com.sol.user.survey.service.SurveyService;
import jakarta.persistence.EntityManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class SalaryDemoPersonaTest {

    private static final Long USER_ID = 50L;

    @Autowired private AssetMockService assetMockService;
    @Autowired private SurveyService surveyService;
    @Autowired private PortfolioRecommendationService recommendationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @MockBean private EtfPoolProvider etfPoolProvider;
    @MockBean private ProductBatchClient productBatchClient;

    @BeforeEach
    void stub() {
        given(etfPoolProvider.getPool()).willReturn(fullPool());
        given(productBatchClient.fetchProducts(anyList())).willAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            Map<Long, ProductBatchItem> m = new HashMap<>();
            for (Long id : ids) m.put(id, new ProductBatchItem(id, "SOL " + id, "ETF", null));
            return m;
        });
        given(productBatchClient.fetchEtfMonthlyDividends(anyList())).willReturn(Map.of());
        seedDemo(USER_ID);
    }

    @Test
    void 데모페르소나는_NORMAL_트랙_3안을_낸다() {
        RecommendationResponse res = recommendWithSurvey(USER_ID, 2, 1, 1);

        assertThat(res.getTrack()).isEqualTo(RecommendationTrack.NORMAL);
        assertThat(res.getPlans()).hasSize(3); // ACTIVE_PLUS tier = 안정·균형·유동성
        assertThat(recommended(res).getMonthlyIncome().signum()).isPositive();
    }

    // ── 공유 헬퍼 (이후 태스크에서 재사용) ────────────────────────────────────────
    void seedDemo(Long userId) {
        jdbc.update("INSERT INTO users (user_id, onboarding_completed, third_party_agreed, marketing_agreed) "
                + "VALUES (?, ?, ?, ?)", userId, false, false, false);
        assetMockService.seed(userId, MockType.SALARY_DEMO, InvestmentPropensity.AGGRESSIVE);
        em.flush();
    }

    RecommendationResponse recommendWithSurvey(Long userId, int q1, int q2, int q3) {
        SurveySaveRequest r = new SurveySaveRequest();
        ReflectionTestUtils.setField(r, "q1", q1);
        ReflectionTestUtils.setField(r, "q2", q2);
        ReflectionTestUtils.setField(r, "q3", q3);
        surveyService.save(userId, r);
        em.clear();
        return recommendationService.recommend(userId);
    }

    PlanResponse planOf(RecommendationResponse res, PlanType type) {
        return res.getPlans().stream().filter(p -> p.getType() == type)
                .findFirst().orElseThrow();
    }

    PlanResponse recommended(RecommendationResponse res) {
        return res.getPlans().stream().filter(p -> p.getStatus() == PlanStatus.RECOMMENDED)
                .findFirst().orElse(res.getPlans().get(0));
    }

    List<EtfInfo> fullPool() {
        List<String> tickers = List.of(
                "433330", "476030", "292500", "446720", "438560", "452360", "436140", "497880");
        List<EtfInfo> pool = new ArrayList<>();
        long pid = 1000L;
        for (String t : tickers) {
            pool.add(new EtfInfo(++pid, t, "SOL " + t,
                    Objects.requireNonNull(PortfolioConstants.RISK_GRADE.get(t)),
                    new BigDecimal("3.00"), "MONTHLY",
                    PortfolioConstants.roleOf(t), PortfolioConstants.currencyOf(t)));
        }
        return pool;
    }
}
