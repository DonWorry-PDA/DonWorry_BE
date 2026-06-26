package com.sol.user.monthlysalary.service;

import com.sol.user.monthlysalary.dto.SalaryPlanConfirmRequest;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.entity.SalaryPlanItem;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 확정 plan DB 보증 통합 — supersede flush 순서·active_user_id 유니크를 H2 실 스키마(create-drop)로 검증한다.
 * ETF 풀 조회(REST)만 목 대체(user-module 단독 컨텍스트라 product-module 부재).
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class SalaryPlanIntegrationTest {

    private static final Long USER_ID = 7001L;

    @Autowired private SalaryPlanService salaryPlanService;
    @Autowired private SalaryPlanRepository salaryPlanRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private EtfPoolProvider etfPoolProvider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO users (user_id, onboarding_completed) VALUES (?, ?)", USER_ID, true);
        given(etfPoolProvider.getPool()).willReturn(List.of(etf(101L), etf(102L)));
    }

    @Test
    void 확정하면_ACTIVE_1건이_gross목표와_파생충당률로_저장된다() {
        salaryPlanService.confirm(USER_ID, request("STABLE", "2000000", "1680000",
                item(101L, "10000000"), item(102L, "5000000")));

        SalaryPlan plan = salaryPlanRepository
                .findWithItemsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(SalaryPlan.STATUS_ACTIVE);
        assertThat(plan.getActiveUserId()).isEqualTo(USER_ID);
        // 충당률 = 1,680,000 / 2,000,000 × 100 = 84.00 (BE 파생)
        assertThat(plan.getLivingCostCoverageRate()).isEqualByComparingTo("84.00");
        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getItems()).extracting(SalaryPlanItem::getTargetAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("10000000"), new BigDecimal("5000000"));
        assertThat(salaryPlanRepository.existsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE)).isTrue();
    }

    @Test
    void 재확정하면_이전ACTIVE는_SUPERSEDED되고_ACTIVE는_여전히_1건() {
        salaryPlanService.confirm(USER_ID, request("STABLE", "2000000", "1680000", item(101L, "10000000")));
        salaryPlanService.confirm(USER_ID, request("BALANCED", "2000000", "1700000", item(102L, "8000000")));

        // 재확정이 유니크 위반 없이 통과 = supersede UPDATE가 신규 INSERT보다 먼저 flush됨.
        List<SalaryPlan> all = salaryPlanRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.stream().filter(p -> SalaryPlan.STATUS_ACTIVE.equals(p.getStatus())).toList())
                .singleElement()
                .satisfies(active -> {
                    assertThat(active.getPlanType()).isEqualTo("BALANCED");
                    assertThat(active.getActiveUserId()).isEqualTo(USER_ID);
                });
        assertThat(all.stream().filter(p -> SalaryPlan.STATUS_SUPERSEDED.equals(p.getStatus())).toList())
                .singleElement()
                .satisfies(superseded -> assertThat(superseded.getActiveUserId()).isNull());
    }

    @Test
    void active_user_id_유니크가_USER당_ACTIVE_2건을_막는다() {
        User user = userRepository.findById(USER_ID).orElseThrow();
        salaryPlanRepository.saveAndFlush(SalaryPlan.active(user, "STABLE",
                new BigDecimal("1680000"), new BigDecimal("84.00"), new BigDecimal("2000000")));

        SalaryPlan duplicate = SalaryPlan.active(user, "BALANCED",
                new BigDecimal("1700000"), new BigDecimal("85.00"), new BigDecimal("2000000"));

        assertThatThrownBy(() -> salaryPlanRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private EtfInfo etf(Long productId) {
        return new EtfInfo(productId, "T" + productId, "ETF" + productId, 5,
                BigDecimal.ZERO, "MONTHLY", null, null);
    }

    private SalaryPlanConfirmRequest request(String planType, String livingCost, String expectedSalary,
                                             SalaryPlanConfirmRequest.HoldingItem... items) {
        return new SalaryPlanConfirmRequest(planType, new BigDecimal(livingCost),
                new BigDecimal(expectedSalary), List.of(items));
    }

    private SalaryPlanConfirmRequest.HoldingItem item(Long productId, String targetAmount) {
        return new SalaryPlanConfirmRequest.HoldingItem(productId, "상품" + productId, "SAFE",
                "BROKERAGE", new BigDecimal("1.00"), new BigDecimal(targetAmount), null);
    }
}
