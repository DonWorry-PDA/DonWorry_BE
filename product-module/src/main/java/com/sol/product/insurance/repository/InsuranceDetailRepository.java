package com.sol.product.insurance.repository;

import com.sol.product.insurance.entity.InsuranceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceDetailRepository extends JpaRepository<InsuranceDetail, Long> { }