package com.sol.user.pension.repository;

import com.sol.user.pension.entity.Pension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;


public interface PensionRepository extends JpaRepository<Pension, Long> {
    void deleteByUserUserId(Long userId);

    List<Pension> findByUserUserId(Long userId);

    List<Pension> findByUserUserIdOrderByPensionIdAsc(Long userId);

    @Query("""
        SELECT p.expectedMonthlyAmount
        FROM Pension p
        WHERE p.user.userId = :userId
          AND p.pensionType = :pensionType
        """)
    Optional<BigDecimal> findMonthlyAmount(@Param("userId") Long userId,
                                           @Param("pensionType") String pensionType);
}
