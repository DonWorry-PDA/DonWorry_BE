package com.sol.user.monthlysalary.repository;

import com.sol.user.monthlysalary.entity.SalaryAssetExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface SalaryAssetExclusionRepository extends JpaRepository<SalaryAssetExclusion, Long> {
    @Query("""
        SELECT s.assetKey
        FROM SalaryAssetExclusion s
        WHERE s.user.userId = :userId
        """)
    Set<String> findAssetKeysByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SalaryAssetExclusion s WHERE s.user.userId = :userId")
    void deleteByUserUserId(@Param("userId") Long userId);
}
