package com.sol.user.notification.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "알림")
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

    @Column(name = "알림유형", length = 20)
    private String notificationType;

    @Column(name = "알림내용", length = 300)
    private String content;

    @Column(name = "link_target", length = 100)
    private String linkTarget;

    @Column(name = "읽음여부")
    private Boolean read;

    @Column(name = "생성시간")
    private LocalDateTime createdAt;
}
