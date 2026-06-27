package com.sol.user.monthlysalary.repository;

import com.sol.user.monthlysalary.entity.SalaryPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalaryPlanRepository extends JpaRepository<SalaryPlan, Long> {

    /** 허브 분기 플래그용 — ACTIVE plan 존재 여부. */
    boolean existsByUserUserIdAndStatus(Long userId, String status);

    /** 상담 예약의 planId 소유권 검증용 — 해당 plan이 이 사용자 소유인지. */
    boolean existsByPlanIdAndUserUserId(Long planId, Long userId);

    /** 재확정 시 supersede 대상 조회(items 불필요). ACTIVE는 유니크라 최대 1건. */
    Optional<SalaryPlan> findByUserUserIdAndStatus(Long userId, String status);

    /** 운용현황 조회 — ACTIVE plan + items 한 번에 fetch. */
    @Query("""
            SELECT p FROM SalaryPlan p
            LEFT JOIN FETCH p.items
            WHERE p.user.userId = :userId AND p.status = :status
            """)
    Optional<SalaryPlan> findWithItemsByUserUserIdAndStatus(@Param("userId") Long userId,
                                                            @Param("status") String status);
}
