package com.sol.user.holding.repository;

import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    void deleteByAccountUserUserId(Long userId);
}
