package com.sol.user.holding.repository;

import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/sql/holding-calendar-product-tables.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class HoldingRepositoryCalendarTest {

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsLatestDividendInputsAndAggregatesUsersQuantityAcrossAccounts() {
        jdbcTemplate.update(
                "INSERT INTO users (user_id, onboarding_completed) VALUES (?, ?), (?, ?)",
                9001L, false, 9002L, false
        );
        jdbcTemplate.update(
                "INSERT INTO account (account_id, user_id) VALUES (?, ?), (?, ?), (?, ?)",
                9101L, 9001L, 9102L, 9001L, 9103L, 9002L
        );
        jdbcTemplate.update(
                "INSERT INTO financial_product (product_id, product_name) VALUES (?, ?)",
                9201L, "SOL 미국배당다우존스"
        );
        jdbcTemplate.update(
                "INSERT INTO etf_detail (etf_detail_id, product_id, distribution_interval_months) VALUES (?, ?, ?)",
                9301L, 9201L, 3
        );
        jdbcTemplate.update(
                "INSERT INTO dividend_history (dist_id, product_id, payment_date, amount_per_unit) "
                        + "VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
                9401L, 9201L, LocalDate.of(2025, 9, 15), 80,
                9402L, 9201L, LocalDate.of(2025, 12, 15), 100
        );
        jdbcTemplate.update(
                "INSERT INTO holding (holding_id, account_id, product_id, quantity) "
                        + "VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)",
                9501L, 9101L, 9201L, 2.5,
                9502L, 9102L, 9201L, 7.5,
                9503L, 9103L, 9201L, 99
        );

        List<HoldingDividendCalendarProjection> result =
                holdingRepository.findDividendCalendarInputsByUserId(9001L);

        assertThat(result).hasSize(1);
        HoldingDividendCalendarProjection projection = result.get(0);
        assertThat(projection.getProductId()).isEqualTo(9201L);
        assertThat(projection.getProductName()).isEqualTo("SOL 미국배당다우존스");
        assertThat(projection.getQuantity()).isEqualByComparingTo("10.0");
        assertThat(projection.getAmountPerUnit()).isEqualByComparingTo("100");
        assertThat(projection.getLatestPaymentDate()).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(projection.getDistributionIntervalMonths()).isEqualTo(3);
    }
}
