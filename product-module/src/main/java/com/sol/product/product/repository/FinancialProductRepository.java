package com.sol.product.product.repository;

import com.sol.product.product.entity.FinancialProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialProductRepository extends JpaRepository<FinancialProduct, Long> {

    Optional<FinancialProduct> findByProductTypeAndProductName(String productType, String productName);
}