package com.sol.user.notification.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "notification_type", length = 20)
    private String notificationType;

    @Column(name = "content", length = 300)
    private String content;

    @Column(name = "link_target", length = 100)
    private String linkTarget;

    @Column(name = "is_read")
    private Boolean read;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
