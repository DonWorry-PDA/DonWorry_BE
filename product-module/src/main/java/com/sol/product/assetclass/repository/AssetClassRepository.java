package com.sol.product.assetclass.repository;

import com.sol.product.assetclass.entity.AssetClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetClassRepository extends JpaRepository<AssetClass, Integer> {

    Optional<AssetClass> findByAssetClassName(String assetClassName);
}