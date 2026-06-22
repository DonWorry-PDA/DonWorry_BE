package com.sol.user.assetconnection.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_connection")
@Getter
@NoArgsConstructor
public class AssetConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "institution_name", nullable = false, length = 50)
    private String institutionName;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "connection_status", nullable = false, length = 20)
    private String connectionStatus;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    public AssetConnection(User user, String institutionName, String category,
                           String connectionStatus, LocalDateTime lastSyncedAt) {
        this.user = user;
        this.institutionName = institutionName;
        this.category = category;
        this.connectionStatus = connectionStatus;
        this.lastSyncedAt = lastSyncedAt;
    }
}
