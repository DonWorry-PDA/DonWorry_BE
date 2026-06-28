package com.sol.user.notification.provider;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.report.repository.MonthlyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MonthlyReportNotificationProvider implements NotificationProvider {

    private final MonthlyReportRepository monthlyReportRepository;
    private final Clock clock;

    @Override
    public NotificationType getType() {
        return NotificationType.MONTHLY_REPORT;
    }

    @Override
    public List<NotificationTarget> findTargets() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() != 1) {
            return List.of();
        }
        YearMonth prevYm = YearMonth.now(clock).minusMonths(1);
        int prevMonthValue = prevYm.getMonthValue();

        return monthlyReportRepository.findAllByCurrentMonth(prevYm.toString())
                .stream()
                .map(report -> new NotificationTarget(
                        report.getUser().getUserId(),
                        "월간 리포트",
                        prevMonthValue + "월 리포트가 준비됐어요. 지난달을 돌아보세요.",
                        "/report"
                ))
                .toList();
    }
}
