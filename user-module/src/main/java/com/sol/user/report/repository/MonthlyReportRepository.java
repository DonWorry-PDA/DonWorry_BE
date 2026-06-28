package com.sol.user.report.repository;

import com.sol.user.report.entity.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByUserUserIdAndCurrentMonth(Long userId, String month);

    @Query("SELECT r.user.userId FROM MonthlyReport r WHERE r.currentMonth = :currentMonth")
    List<Long> findUserIdsByCurrentMonth(@Param("currentMonth") String currentMonth);
}
