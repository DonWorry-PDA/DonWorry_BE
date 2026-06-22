package com.sol.user.insurance.repository;

import com.sol.user.insurance.entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {
    void deleteByUserUserId(Long userId);

    List<InsurancePolicy> findByUserUserId(Long userId);
}
