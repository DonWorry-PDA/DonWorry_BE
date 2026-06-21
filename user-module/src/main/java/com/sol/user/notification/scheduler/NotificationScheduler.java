package com.sol.user.notification.scheduler;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.notification.provider.NotificationProvider;
import com.sol.user.notification.repository.NotificationRepository;
import com.sol.user.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final List<NotificationProvider> providers;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 9 * * *")
    public void run() {
        Set<String> sentToday = loadSentToday();

        for (NotificationProvider provider : providers) {
            NotificationType type = provider.getType();
            List<NotificationTarget> targets = provider.findTargets();

            for (NotificationTarget target : targets) {
                String key = target.userId() + ":" + type.name();
                if (sentToday.contains(key)) {
                    log.debug("중복 알림 스킵 userId={} type={}", target.userId(), type);
                    continue;
                }
                notificationService.notify(target.userId(), type, target.title(), target.content(), target.linkTarget());
                sentToday.add(key);
            }
        }
    }

    private Set<String> loadSentToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<Object[]> rows = notificationRepository.findSentPairsToday(startOfDay);

        Set<String> keys = new HashSet<>();
        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            NotificationType type = (NotificationType) row[1];
            keys.add(userId + ":" + type.name());
        }
        return keys;
    }
}
