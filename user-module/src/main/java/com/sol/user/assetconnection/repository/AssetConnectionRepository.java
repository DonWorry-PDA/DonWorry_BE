package com.sol.user.assetconnection.repository;

import com.sol.user.assetconnection.entity.AssetConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetConnectionRepository extends JpaRepository<AssetConnection, Long> {
    void deleteByUserUserId(Long userId);
}
