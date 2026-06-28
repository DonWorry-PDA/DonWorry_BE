package com.sol.user.notification.provider;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.repository.MonthlyReportRepository;
import com.sol.user.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonthlyReportNotificationProviderTest {

    @Mock
    MonthlyReportRepository monthlyReportRepository;

    @Mock
    Clock clock;

    @InjectMocks
    MonthlyReportNotificationProvider provider;

    @Test
    @DisplayName("타입이 MONTHLY_REPORT이다")
    void returnsCorrectType() {
        assertThat(provider.getType()).isEqualTo(NotificationType.MONTHLY_REPORT);
    }

    @Test
    @DisplayName("매월 1일에 전달 스냅샷이 있는 유저에게 알림 대상을 반환한다")
    void returnsTargetsOnFirstDayOfMonth() {
        fixedClock("2026-07-01");

        User user1 = mock(User.class);
        given(user1.getUserId()).willReturn(1L);
        User user2 = mock(User.class);
        given(user2.getUserId()).willReturn(3L);

        MonthlyReport report1 = MonthlyReport.snapshot(user1, "2026-06", BigDecimal.valueOf(250_000_000));
        MonthlyReport report2 = MonthlyReport.snapshot(user2, "2026-06", BigDecimal.valueOf(180_000_000));
        given(monthlyReportRepository.findAllByCurrentMonth("2026-06")).willReturn(List.of(report1, report2));

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).userId()).isEqualTo(1L);
        assertThat(targets.get(0).title()).isEqualTo("월간 리포트");
        assertThat(targets.get(0).content()).contains("6월");
        assertThat(targets.get(0).linkTarget()).isEqualTo("/report");
        assertThat(targets.get(1).userId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("1일이 아니면 빈 리스트를 반환하고 DB를 조회하지 않는다")
    void returnsEmptyWhenNotFirstDayOfMonth() {
        fixedClock("2026-06-27");

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).isEmpty();
        verify(monthlyReportRepository, never()).findAllByCurrentMonth(any());
    }

    @Test
    @DisplayName("1일이지만 전달 스냅샷이 없으면 빈 리스트를 반환한다")
    void returnsEmptyWhenNoPrevMonthSnapshot() {
        fixedClock("2026-07-01");
        given(monthlyReportRepository.findAllByCurrentMonth("2026-06")).willReturn(List.of());

        assertThat(provider.findTargets()).isEmpty();
    }

    private void fixedClock(String date) {
        Instant instant = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant();
        given(clock.instant()).willReturn(instant);
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }
}
