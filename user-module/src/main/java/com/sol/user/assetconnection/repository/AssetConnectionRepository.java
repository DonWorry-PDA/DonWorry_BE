package com.sol.user.assetconnection.repository;

import com.sol.user.assetconnection.entity.AssetConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetConnectionRepository extends JpaRepository<AssetConnection, Long> {
    void deleteByUserUserId(Long userId);

    List<AssetConnection> findByUserUserId(Long userId);
}
