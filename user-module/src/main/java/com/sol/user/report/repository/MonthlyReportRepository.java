package com.sol.user.report.repository;

import com.sol.user.report.entity.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByUserUserIdAndCurrentMonth(Long userId, String month);
}
