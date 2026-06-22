package com.sol.user.monthlysalary.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SalaryAssetExclusion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exclusion_id")
    private Long exclusionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "asset_key", nullable = false, length = 50)
    private String assetKey;

    @Column(name = "excluded_at", nullable = false, updatable = false)
    private LocalDateTime excludedAt;

    @PrePersist
    protected void onCreate() {
        this.excludedAt = LocalDateTime.now();
    }
}
