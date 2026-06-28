package com.sol.user.notification.provider;

import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PensionDepositNotificationProviderTest {

    @Mock
    CashFlowEventRepository cashFlowEventRepository;

    @Mock
    Clock clock;

    @InjectMocks
    PensionDepositNotificationProvider provider;

    @Test
    @DisplayName("타입이 PENSION_DEPOSIT이다")
    void returnsCorrectType() {
        assertThat(provider.getType()).isEqualTo(NotificationType.PENSION_DEPOSIT);
    }

    @Test
    @DisplayName("오늘 PENSION 이벤트가 있는 유저에게 알림 대상을 반환한다")
    void returnsTargetsForTodayPensionEvents() {
        LocalDate today = fixedClock("2026-06-28");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, BigDecimal.valueOf(1_200_000)});
        rows.add(new Object[]{2L, BigDecimal.valueOf(900_000)});
        given(cashFlowEventRepository.findUserIdAndTotalAmountByEventTypeAndDate(eq("PENSION"), eq(today)))
                .willReturn(rows);

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).userId()).isEqualTo(1L);
        assertThat(targets.get(0).title()).isEqualTo("연금 입금");
        assertThat(targets.get(0).content()).contains("1,200,000원");
        assertThat(targets.get(0).linkTarget()).isEqualTo("/calendar");
        assertThat(targets.get(1).userId()).isEqualTo(2L);
        assertThat(targets.get(1).content()).contains("900,000원");
    }

    @Test
    @DisplayName("오늘 PENSION 이벤트가 없으면 빈 리스트를 반환한다")
    void returnsEmptyWhenNoEvents() {
        LocalDate today = fixedClock("2026-06-28");
        given(cashFlowEventRepository.findUserIdAndTotalAmountByEventTypeAndDate(eq("PENSION"), eq(today)))
                .willReturn(new ArrayList<>());

        assertThat(provider.findTargets()).isEmpty();
    }

    private LocalDate fixedClock(String date) {
        LocalDate localDate = LocalDate.parse(date);
        Instant instant = localDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        given(clock.instant()).willReturn(instant);
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        return localDate;
    }
}
