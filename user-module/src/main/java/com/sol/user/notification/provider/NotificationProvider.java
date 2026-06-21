package com.sol.user.notification.provider;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;

import java.util.List;

public interface NotificationProvider {

    NotificationType getType();

    List<NotificationTarget> findTargets();
}
