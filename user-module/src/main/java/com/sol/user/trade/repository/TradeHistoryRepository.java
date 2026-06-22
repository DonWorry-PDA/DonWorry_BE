package com.sol.user.trade.repository;

import com.sol.user.trade.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    void deleteByAccountUserUserId(Long userId);
}
