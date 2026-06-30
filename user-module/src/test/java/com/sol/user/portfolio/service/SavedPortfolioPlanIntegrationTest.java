package com.sol.user.portfolio.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.entity.SavedPortfolioPlanItem;
import com.sol.user.portfolio.repository.SavedPortfolioPlanRepository;
import com.sol.user.portfolio.type.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
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
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (user_id, onboarding_completed, third_party_agreed, marketing_agreed)
                VALUES (?, ?, ?, ?)
                """, USER_ID, true, false, false);
    }

    @Test
    void 저장_후_헤더와_종목이_DB에_저장된다() {
        SavePlanResponse response = service.save(USER_ID, request(PlanType.STABLE,
                item(101L, "KODEX 200", "069500", "120000000"),
                item(102L, "TIGER 미국채", "305080", "80000000")));

        assertThat(response.id()).isNotNull();

        SavedPortfolioPlan plan = repository.findById(response.id()).orElseThrow();
        assertThat(plan.getPlanType()).isEqualTo(PlanType.STABLE);
        assertThat(plan.getMonthlyIncome()).isEqualByComparingTo("1680000");
        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getItems()).extracting(SavedPortfolioPlanItem::getProductId)
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void 같은_유저가_여러_번_저장하면_건수가_늘어난다() {
        service.save(USER_ID, request(PlanType.STABLE, item(101L, "KODEX 200", "069500", "120000000")));
        service.save(USER_ID, request(PlanType.BALANCED, item(102L, "TIGER S&P500", "360750", "200000000")));

        List<SavedPortfolioPlan> all = repository.findByUserUserId(USER_ID);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(SavedPortfolioPlan::getPlanType)
                .containsExactlyInAnyOrder(PlanType.STABLE, PlanType.BALANCED);
    }

    @Test
    void planId와_userId_일치하면_해당_건만_삭제된다() {
        SavePlanResponse first = service.save(USER_ID, request(PlanType.STABLE, item(101L, "KODEX 200", "069500", "120000000")));
        service.save(USER_ID, request(PlanType.BALANCED, item(102L, "TIGER S&P500", "360750", "200000000")));

        service.delete(USER_ID, first.id());

        List<SavedPortfolioPlan> remaining = repository.findByUserUserId(USER_ID);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getPlanType()).isEqualTo(PlanType.BALANCED);
    }

    @Test
    void 다른_유저_planId로_삭제_시도하면_SAVED_PLAN_NOT_FOUND() {
        SavePlanResponse saved = service.save(USER_ID, request(PlanType.STABLE, item(101L, "KODEX 200", "069500", "120000000")));
        Long otherUserId = 9999L;

        assertThatThrownBy(() -> service.delete(otherUserId, saved.id()))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.SAVED_PLAN_NOT_FOUND);
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
