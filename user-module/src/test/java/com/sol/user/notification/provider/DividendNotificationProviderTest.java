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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DividendNotificationProviderTest {

    @Mock
    CashFlowEventRepository cashFlowEventRepository;

    @InjectMocks
    DividendNotificationProvider provider;

    @Test
    @DisplayName("타입이 DIVIDEND이다")
    void returnsCorrectType() {
        assertThat(provider.getType()).isEqualTo(NotificationType.DIVIDEND);
    }

    @Test
    @DisplayName("오늘 DIVIDEND 이벤트가 있는 유저에게 알림 대상을 반환한다")
    void returnsTargetsForTodayDividendEvents() {
        LocalDate today = LocalDate.now();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, BigDecimal.valueOf(500_000)});
        rows.add(new Object[]{3L, BigDecimal.valueOf(1_000_000)});
        given(cashFlowEventRepository.findUserIdAndTotalAmountByEventTypeAndDate(eq("DIVIDEND"), eq(today)))
                .willReturn(rows);

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).userId()).isEqualTo(1L);
        assertThat(targets.get(0).title()).isEqualTo("배당금 입금");
        assertThat(targets.get(0).content()).contains("500,000원");
        assertThat(targets.get(0).linkTarget()).isEqualTo("/calendar");
        assertThat(targets.get(1).userId()).isEqualTo(3L);
        assertThat(targets.get(1).content()).contains("1,000,000원");
    }

    @Test
    @DisplayName("같은 유저의 여러 ETF 배당이 합산되어 반환된다")
    void sumsDividendAmountsPerUser() {
        LocalDate today = LocalDate.now();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, BigDecimal.valueOf(1_500_000)});
        given(cashFlowEventRepository.findUserIdAndTotalAmountByEventTypeAndDate(eq("DIVIDEND"), eq(today)))
                .willReturn(rows);

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).content()).contains("1,500,000원");
    }

    @Test
    @DisplayName("오늘 DIVIDEND 이벤트가 없으면 빈 리스트를 반환한다")
    void returnsEmptyWhenNoEvents() {
        LocalDate today = LocalDate.now();
        given(cashFlowEventRepository.findUserIdAndTotalAmountByEventTypeAndDate(eq("DIVIDEND"), eq(today)))
                .willReturn(new ArrayList<>());

        assertThat(provider.findTargets()).isEmpty();
    }
}
