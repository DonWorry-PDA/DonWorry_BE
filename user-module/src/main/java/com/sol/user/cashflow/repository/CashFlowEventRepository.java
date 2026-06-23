package com.sol.user.cashflow.repository;

import com.sol.user.cashflow.entity.CashFlowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CashFlowEventRepository extends JpaRepository<CashFlowEvent, Long> {
    void deleteByUserUserId(Long userId);

    List<CashFlowEvent> findByUserUserId(Long userId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM CashFlowEvent e
        WHERE e.user.userId = :userId
          AND e.flowType = 'INCOME'
          AND e.eventType IN ('INTEREST', 'DIVIDEND')
        """)
    BigDecimal sumMonthlyFinancialIncomeByUserId(@Param("userId") Long userId);
}
