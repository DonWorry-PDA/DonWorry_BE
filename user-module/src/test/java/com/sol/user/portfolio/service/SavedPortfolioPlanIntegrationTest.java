package com.sol.user.portfolio.service;

import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.entity.SavedPortfolioPlanItem;
import com.sol.user.portfolio.repository.SavedPortfolioPlanRepository;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class SavedPortfolioPlanIntegrationTest {

    private static final Long USER_ID = 7002L;

    @Autowired private SavedPortfolioPlanService service;
    @Autowired private SavedPortfolioPlanRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (user_id, onboarding_completed, third_party_agreed, marketing_agreed)
                VALUES (?, ?, ?, ?)
                """, USER_ID, true, false, false);
    }

    @Test
    void 최초_저장_후_헤더와_종목이_DB에_저장된다() {
        service.save(USER_ID, request(PlanType.STABLE,
                item(101L, "KODEX 200", "069500", "120000000"),
                item(102L, "TIGER 미국채", "305080", "80000000")));

        SavedPortfolioPlan plan = repository.findByUserUserId(USER_ID).orElseThrow();
        assertThat(plan.getPlanType()).isEqualTo(PlanType.STABLE);
        assertThat(plan.getMonthlyIncome()).isEqualByComparingTo("1680000");
        assertThat(plan.getCurrentCoverageRate()).isEqualByComparingTo("59.00");
        assertThat(plan.getTotalCoverageRate()).isEqualByComparingTo("84.00");
        assertThat(plan.getCurrentMonthlyShortfall()).isEqualByComparingTo("900000");
        assertThat(plan.getResidualMonthlyShortfall()).isEqualByComparingTo("0");
        assertThat(plan.getPrincipalAmount()).isEqualByComparingTo("200000000");
        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getItems()).extracting(SavedPortfolioPlanItem::getProductId)
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void 재저장하면_설계안은_1건으로_유지되고_종목이_교체된다() {
        service.save(USER_ID, request(PlanType.STABLE, item(101L, "KODEX 200", "069500", "120000000")));
        service.save(USER_ID, request(PlanType.BALANCED, item(102L, "TIGER S&P500", "360750", "200000000")));

        List<SavedPortfolioPlan> all = repository.findAll().stream()
                .filter(p -> p.getUser().getUserId().equals(USER_ID))
                .toList();
        assertThat(all).hasSize(1);

        SavedPortfolioPlan plan = all.get(0);
        assertThat(plan.getPlanType()).isEqualTo(PlanType.BALANCED);
        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getProductId()).isEqualTo(102L);
    }

    @Test
    void user_id_유니크가_직접_중복_삽입을_막는다() {
        User user = userRepository.findById(USER_ID).orElseThrow();

        SavedPortfolioPlan first = SavedPortfolioPlan.builder()
                .user(user).planType(PlanType.STABLE)
                .monthlyIncome(new BigDecimal("1680000"))
                .currentCoverageRate(new BigDecimal("59.00"))
                .totalCoverageRate(new BigDecimal("84.00"))
                .currentMonthlyShortfall(new BigDecimal("900000"))
                .residualMonthlyShortfall(BigDecimal.ZERO)
                .principalAmount(new BigDecimal("200000000"))
                .savedAt(java.time.LocalDateTime.now())
                .build();
        repository.saveAndFlush(first);

        SavedPortfolioPlan duplicate = SavedPortfolioPlan.builder()
                .user(user).planType(PlanType.BALANCED)
                .monthlyIncome(new BigDecimal("1700000"))
                .currentCoverageRate(new BigDecimal("59.00"))
                .totalCoverageRate(new BigDecimal("85.00"))
                .currentMonthlyShortfall(new BigDecimal("900000"))
                .residualMonthlyShortfall(BigDecimal.ZERO)
                .principalAmount(new BigDecimal("200000000"))
                .savedAt(java.time.LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SavePlanRequest request(PlanType planType, SavePlanRequest.HoldingItem... items) {
        return new SavePlanRequest(
                planType,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(items));
    }

    private SavePlanRequest.HoldingItem item(Long productId, String name, String ticker, String amount) {
        return new SavePlanRequest.HoldingItem(productId, ticker, name,
                new BigDecimal("0.60"), new BigDecimal(amount));
    }
}
