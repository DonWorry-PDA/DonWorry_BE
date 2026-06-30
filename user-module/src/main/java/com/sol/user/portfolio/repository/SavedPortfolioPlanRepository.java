package com.sol.user.portfolio.repository;

import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedPortfolioPlanRepository extends JpaRepository<SavedPortfolioPlan, Long> {
    Optional<SavedPortfolioPlan> findByUserUserId(Long userId);
}
