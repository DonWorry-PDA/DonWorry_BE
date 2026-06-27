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

    /** 특정 기간(보통 당월) 내 flowType(INCOME/EXPENSE) 금액 합계. 없으면 0.
     *  주식 매수·매도(STOCK_BUY/STOCK_SELL)는 투자 거래로 홈 수입·지출 집계에서 제외한다. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM CashFlowEvent e
            WHERE e.user.userId = :userId
              AND e.flowType = :flowType
              AND e.eventType NOT IN ('STOCK_BUY', 'STOCK_SELL')
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

    /** 특정 기간 내 eventType별 금액 합계. 월간 리포트 배당/이자 항목 분리 집계용. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM CashFlowEvent e
            WHERE e.user.userId = :userId
              AND e.eventType = :eventType
              AND e.eventDate BETWEEN :start AND :end
            """)
    BigDecimal sumAmountByEventTypeInPeriod(
            @Param("userId") Long userId,
            @Param("eventType") String eventType,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /** 당월 recurring 고정지출 합계. 다음 달 나갈 돈 예측 용도(당월 고정지출 = 다음 달 예상 고정지출). */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM CashFlowEvent e
            WHERE e.user.userId = :userId
              AND e.flowType = 'EXPENSE'
              AND e.recurring = true
              AND e.eventDate BETWEEN :start AND :end
            """)
    BigDecimal sumRecurringExpenseInPeriod(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

}