package com.sol.user.notification.repository;

import com.sol.user.notification.entity.Notification;
import com.sol.user.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByUserUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.userId = :userId AND n.read = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    @Query("SELECT n.user.userId, n.notificationType FROM Notification n WHERE n.createdAt >= :startOfDay")
    List<Object[]> findSentPairsToday(@Param("startOfDay") LocalDateTime startOfDay);
}
