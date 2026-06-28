package com.sol.user.notification.repository;

import com.sol.user.notification.entity.Notification;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class NotificationUnreadCountRepositoryTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("읽지 않은 알림만 카운트한다")
    void countsOnlyUnreadNotifications() {
        User user = userRepository.save(new User());

        Notification unread1 = save(user, NotificationType.DIVIDEND);
        Notification unread2 = save(user, NotificationType.PENSION_DEPOSIT);
        Notification read = save(user, NotificationType.MONTHLY_REPORT);
        read.markAsRead();

        long count = notificationRepository.countByUserUserIdAndReadFalse(user.getUserId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("알림이 없으면 0을 반환한다")
    void returnsZeroWhenNoNotifications() {
        User user = userRepository.save(new User());

        long count = notificationRepository.countByUserUserIdAndReadFalse(user.getUserId());

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("다른 유저의 읽지 않은 알림은 카운트하지 않는다")
    void doesNotCountOtherUsersNotifications() {
        User userA = userRepository.save(new User());
        User userB = userRepository.save(new User());
        save(userA, NotificationType.DIVIDEND);
        save(userA, NotificationType.PENSION_DEPOSIT);

        long count = notificationRepository.countByUserUserIdAndReadFalse(userB.getUserId());

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("전체 읽음 처리 후 카운트는 0이다")
    void returnsZeroAfterMarkAllAsRead() {
        User user = userRepository.save(new User());
        save(user, NotificationType.DIVIDEND);
        save(user, NotificationType.PENSION_DEPOSIT);

        notificationRepository.markAllAsReadByUserId(user.getUserId());
        long count = notificationRepository.countByUserUserIdAndReadFalse(user.getUserId());

        assertThat(count).isZero();
    }

    private Notification save(User user, NotificationType type) {
        return notificationRepository.save(Notification.builder()
                .user(user)
                .notificationType(type)
                .title("테스트 알림")
                .content("테스트 내용")
                .build());
    }
}
