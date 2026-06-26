package com.sol.user.debt.repository;

import com.sol.user.debt.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    void deleteByUserUserId(Long userId);

    List<Debt> findByUserUserId(Long userId);

    List<Debt> findByUserUserIdAndMaturityDateBetweenOrderByMaturityDateAscIdAsc(
            Long userId,
            LocalDate from,
            LocalDate to
    );

    @Query("SELECT COALESCE(SUM(d.balance), 0) FROM Debt d WHERE d.user.userId = :userId")
    BigDecimal sumBalanceByUserId(@Param("userId") Long userId);
}
