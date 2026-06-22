package com.sol.user.cashflow.repository;

import com.sol.user.cashflow.entity.CashFlowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashFlowEventRepository extends JpaRepository<CashFlowEvent, Long> {
    void deleteByUserUserId(Long userId);

    List<CashFlowEvent> findByUserUserId(Long userId);

}
