package com.sol.user.notification.provider;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketOpenReminderNotificationProvider implements NotificationProvider {

    private final NotificationSettingRepository notificationSettingRepository;

    @Override
    public NotificationType getType() {
        return NotificationType.MARKET_OPEN_REMINDER;
    }

    @Override
    @Transactional
    public List<NotificationTarget> findTargets() {
        List<NotificationSetting> subscribers = notificationSettingRepository.findByMarketOpenReminderTrue();
        if (subscribers.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = subscribers.stream()
                .map(s -> s.getUser().getUserId())
                .toList();
        notificationSettingRepository.clearMarketOpenRemindersByUserIds(userIds);
        return subscribers.stream()
                .map(s -> new NotificationTarget(
                        s.getUser().getUserId(),
                        "장 시작 알림",
                        "오늘 장이 열렸어요. 지금 월급 만들기를 시작해보세요.",
                        "/monthly-salary"
                ))
                .toList();
    }
}
