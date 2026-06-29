package com.sol.user.trade.repository;

import com.sol.user.trade.entity.TradeHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    void deleteByAccountUserUserId(Long userId);

    boolean existsByAccountAccountId(Long accountId);

    @EntityGraph(attributePaths = "account")
    List<TradeHistory> findByAccountUserUserIdAndTradedAtGreaterThanEqualAndTradedAtLessThanOrderByTradedAtDescOrderIdDesc(
            Long userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );
}
