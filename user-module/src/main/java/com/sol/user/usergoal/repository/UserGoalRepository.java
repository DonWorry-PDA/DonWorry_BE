package com.sol.user.usergoal.repository;

import com.sol.user.usergoal.entity.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {
    void deleteByUserUserId(Long userId);

    Optional<UserGoal> findTopByUserUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UserGoal> findByUserUserId(Long userId);
}
