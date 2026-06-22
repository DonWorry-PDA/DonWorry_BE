package com.sol.user.stability.repository;

import com.sol.user.stability.entity.StabilityScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StabilityScoreRepository extends JpaRepository<StabilityScore, Long> {

    Optional<StabilityScore> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
