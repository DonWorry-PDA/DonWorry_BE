package com.sol.user.notification.repository;

import com.sol.user.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByMarketOpenReminderTrue();

    @Modifying
    @Query("UPDATE NotificationSetting s SET s.marketOpenReminder = false WHERE s.marketOpenReminder = true")
    void clearAllMarketOpenReminders();
}
