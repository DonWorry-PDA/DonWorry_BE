package com.sol.user.cashflow.repository;

import com.sol.user.cashflow.entity.CashFlowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
