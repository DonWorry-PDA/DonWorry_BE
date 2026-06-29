package com.sol.user.notification.provider;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.notification.repository.NotificationSettingRepository;
import com.sol.user.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketOpenReminderNotificationProviderTest {

    @Mock
    NotificationSettingRepository notificationSettingRepository;

    @InjectMocks
    MarketOpenReminderNotificationProvider provider;

    @Test
    @DisplayName("타입이 MARKET_OPEN_REMINDER이다")
    void returnsCorrectType() {
        assertThat(provider.getType()).isEqualTo(NotificationType.MARKET_OPEN_REMINDER);
    }

    @Test
    @DisplayName("구독한 유저들에게 알림 대상을 반환하고 플래그를 초기화한다")
    void returnsTargetsAndClearsFlags() {
        NotificationSetting s1 = settingWithReminder(1L);
        NotificationSetting s2 = settingWithReminder(2L);
        given(notificationSettingRepository.findByMarketOpenReminderTrue()).willReturn(List.of(s1, s2));

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).userId()).isEqualTo(1L);
        assertThat(targets.get(0).title()).isEqualTo("장 시작 알림");
        assertThat(targets.get(0).linkTarget()).isEqualTo("/monthly-salary");
        assertThat(targets.get(1).userId()).isEqualTo(2L);
        verify(notificationSettingRepository).clearMarketOpenRemindersByUserIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("구독자가 없으면 빈 리스트를 반환하고 플래그 초기화를 호출하지 않는다")
    void returnsEmptyWhenNoSubscribers() {
        given(notificationSettingRepository.findByMarketOpenReminderTrue()).willReturn(List.of());

        List<NotificationTarget> targets = provider.findTargets();

        assertThat(targets).isEmpty();
        verify(notificationSettingRepository, never()).clearMarketOpenRemindersByUserIds(anyList());
    }

    private NotificationSetting settingWithReminder(Long userId) {
        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);
        NotificationSetting setting = NotificationSetting.defaultFor(user);
        setting.subscribeMarketOpenReminder();
        return setting;
    }
}
