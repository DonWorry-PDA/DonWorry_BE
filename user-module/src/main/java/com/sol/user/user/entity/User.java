package com.sol.user.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "사용자")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "이름", length = 50)
    private String name;

    @Column(name = "나이")
    private Integer age;

    @Column(name = "비밀번호", length = 255)
    private String password;

    @Column(name = "은퇴여부")
    private Boolean retired;

    @Column(name = "국민연금수령여부")
    private Boolean nationalPensionReceiving;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
