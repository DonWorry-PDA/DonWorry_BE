package com.sol.user.pension.repository;

import com.sol.user.pension.entity.Pension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PensionRepository extends JpaRepository<Pension, Long> {
    void deleteByUserUserId(Long userId);

    List<Pension> findByUserUserId(Long userId);
}
