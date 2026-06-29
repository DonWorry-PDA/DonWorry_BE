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

    @Test
    void q3_상속vs소비_변경시_추천안_월수령이_20퍼센트_이상_달라진다() {
        // q1·q2 고정, q3만: 0(상속우선)·2(소비우선). 소진비율 0.3 vs 1.0 → 소비우선이 월수령 ↑.
        BigDecimal incInherit = recommended(recommendWithSurvey(USER_ID, 2, 1, 0)).getMonthlyIncome();
        BigDecimal incSpend = recommended(recommendWithSurvey(USER_ID, 2, 1, 2)).getMonthlyIncome();

        assertThat(incSpend).as("소비우선(q3=2) 월수령 > 상속우선(q3=0)")
                .isGreaterThan(incInherit);
        double swing = incSpend.subtract(incInherit).doubleValue() / incInherit.doubleValue();
        assertThat(swing).as("q3 월수령 스윙 ≥ 20%%").isGreaterThanOrEqualTo(0.20);
    }

    @Test
    void q1q2_성향설문_변경시_위험배분이_크게_달라진다() {
        // q3 고정, 선호점수 최저(q1=0,q2=0) vs 최고(q1=3,q2=2) → 운용등급 ↑ → 위험비중 ↑.
        // 안정안(STABLE) 기준으로 비교.
        BigDecimal riskLow = planOf(recommendWithSurvey(USER_ID, 0, 0, 1), PlanType.STABLE).getRiskTarget();
        BigDecimal riskHigh = planOf(recommendWithSurvey(USER_ID, 3, 2, 1), PlanType.STABLE).getRiskTarget();

        assertThat(riskLow.signum()).as("저성향에서도 위험배분 > 0").isPositive();
        assertThat(riskHigh).as("고성향 위험배분 > 저성향")
                .isGreaterThan(riskLow);
        double ratio = riskHigh.doubleValue() / riskLow.doubleValue();
        assertThat(ratio).as("위험배분 ≥ 1.3배 확대").isGreaterThanOrEqualTo(1.3);
    }

    @Test
    void 설문을_다시_저장하면_추천_결과가_바뀐다() {
        // 재진입 흐름의 핵심 보장: POST /survey 덮어쓰기 후 recommend가 새 설문을 반영한다.
        BigDecimal before = recommended(recommendWithSurvey(USER_ID, 2, 1, 0)).getMonthlyIncome();
        BigDecimal after = recommended(recommendWithSurvey(USER_ID, 2, 1, 2)).getMonthlyIncome();

        assertThat(after).as("설문 변경 후 월수령이 달라짐").isNotEqualByComparingTo(before);
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
