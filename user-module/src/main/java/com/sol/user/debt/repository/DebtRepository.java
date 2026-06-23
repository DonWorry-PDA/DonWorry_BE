package com.sol.user.debt.repository;

import com.sol.user.debt.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
