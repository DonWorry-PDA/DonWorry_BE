package com.sol.user.cashflow.repository;

import com.sol.user.cashflow.entity.CashFlowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CashFlowEventRepository extends JpaRepository<CashFlowEvent, Long> {
    void deleteByUserUserId(Long userId);

    List<CashFlowEvent> findByUserUserId(Long userId);

    List<CashFlowEvent> findByUserUserIdAndEventDateBetweenOrderByEventDateDescEventIdDesc(
            Long userId,
            LocalDate from,
            LocalDate to
    );

    @Query("""
            SELECT event
            FROM CashFlowEvent event
            WHERE event.user.userId = :userId
              AND (event.recurring = true OR event.eventDate BETWEEN :from AND :to)
            ORDER BY event.eventDate ASC, event.eventId ASC
            """)
    List<CashFlowEvent> findCalendarEvents(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** 특정 기간(보통 당월) 내 flowType(INCOME/EXPENSE) 금액 합계. 없으면 0. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM CashFlowEvent e
            WHERE e.user.userId = :userId
              AND e.flowType = :flowType
              AND e.eventDate BETWEEN :start AND :end
            """)
    BigDecimal sumAmountByFlowTypeInPeriod(
            @Param("userId") Long userId,
            @Param("flowType") String flowType,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /** 사용자의 총 월 금융 수입(이자+배당) 합계. 없으면 0. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM CashFlowEvent e
            WHERE e.user.userId = :userId
              AND e.flowType = 'INCOME'
              AND e.eventType IN ('INTEREST', 'DIVIDEND')
              AND e.recurring = true
            """)
    BigDecimal sumMonthlyFinancialIncomeByUserId(@Param("userId") Long userId);

}