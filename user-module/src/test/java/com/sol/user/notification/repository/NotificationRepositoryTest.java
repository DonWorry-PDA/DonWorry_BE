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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("알림 1건 저장 후 userId로 조회 시 반환된다")
    void saveAndFindByUserId() {
        User user = userRepository.save(new User());

        Notification notification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.BALANCE_ALERT)
                .title("잔액 부족 안내")
                .content("다음 달 지출 예상액이 잔액을 초과할 수 있습니다.")
                .build();
        notificationRepository.save(notification);

        List<Notification> result = notificationRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNotificationType()).isEqualTo(NotificationType.BALANCE_ALERT);
        assertThat(result.get(0).getTitle()).isEqualTo("잔액 부족 안내");
        assertThat(result.get(0).getRead()).isFalse();
    }

    @Test
    @DisplayName("다른 유저의 알림은 조회되지 않는다")
    void findByUserId_doesNotReturnOtherUsersNotifications() {
        User userA = userRepository.save(new User());
        User userB = userRepository.save(new User());

        notificationRepository.save(Notification.builder()
                .user(userA)
                .notificationType(NotificationType.DIVIDEND)
                .title("배당금 입금 안내")
                .content("삼성전자 배당금이 내일 지급됩니다.")
                .build());

        List<Notification> result = notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userB.getUserId());

        assertThat(result).isEmpty();
    }
}
