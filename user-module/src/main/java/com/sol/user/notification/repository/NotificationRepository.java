package com.sol.user.notification.repository;

import com.sol.user.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByUserUserIdOrderByCreatedAtDesc(Long userId);
}
