package com.sol.user.debt.repository;

import com.sol.user.debt.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    void deleteByUserUserId(Long userId);

    List<Debt> findByUserUserId(Long userId);
}
