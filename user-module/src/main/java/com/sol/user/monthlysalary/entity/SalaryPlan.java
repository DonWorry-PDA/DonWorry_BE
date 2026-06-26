package com.sol.user.monthlysalary.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 월급 만들기 확정 plan(완료된 매수의 스냅샷). 존재 ⟺ 매수 완료 ⟺ 기이용자.
 * 마이데이터(holding/trade_history)와 물리적으로 분리된 인앱 확정 액션 전용 레코드.
 *
 * <p>USER당 ACTIVE는 반드시 1건 — {@code active_user_id} UNIQUE로 DB 레벨 강제(MySQL은 NULL 중복 허용,
 * SUPERSEDED는 NULL). {@code ddl-auto: update}가 유니크를 신뢰성 있게 안 걸 수 있으므로 운영 DB에는
 * 수동 {@code ALTER TABLE salary_plan ADD CONSTRAINT uk_salary_plan_active_user UNIQUE (active_user_id);}
 * 반영 확인 필요.
 */
@Entity
@Table(
        name = "salary_plan",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_salary_plan_active_user",
                columnNames = "active_user_id"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SalaryPlan {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 고른 안(STABLE/BALANCED/LIQUIDITY). */
    @Column(name = "plan_type", length = 20)
    private String planType;

    /** 예상 월수령(헤드라인). */
    @Column(name = "expected_monthly_salary", precision = 15, scale = 0)
    private BigDecimal expectedMonthlySalary;

    /** 충당률 = 예상월수령 / 목표생활비 × 100 (BE에서 파생 저장). */
    @Column(name = "living_cost_coverage_rate", precision = 5, scale = 2)
    private BigDecimal livingCostCoverageRate;

    /** 목표 생활비(명시 저장 — 운용현황 비교용). */
    @Column(name = "target_monthly_living_cost", precision = 15, scale = 0)
    private BigDecimal targetMonthlyLivingCost;

    /** ACTIVE / SUPERSEDED. */
    @Column(name = "status", length = 20)
    private String status;

    /** ACTIVE면 userId, SUPERSEDED면 NULL. UNIQUE → USER당 ACTIVE 1건 강제. */
    @Column(name = "active_user_id")
    private Long activeUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "salaryPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalaryPlanItem> items = new ArrayList<>();

    /** 신규 ACTIVE plan 생성 — 매수 완료 직후 스냅샷. */
    public static SalaryPlan active(User user, String planType, BigDecimal expectedMonthlySalary,
                                    BigDecimal livingCostCoverageRate, BigDecimal targetMonthlyLivingCost) {
        return SalaryPlan.builder()
                .user(user)
                .planType(planType)
                .expectedMonthlySalary(expectedMonthlySalary)
                .livingCostCoverageRate(livingCostCoverageRate)
                .targetMonthlyLivingCost(targetMonthlyLivingCost)
                .status(STATUS_ACTIVE)
                .activeUserId(user.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** 재확정 시 기존 ACTIVE를 비활성화 — active_user_id를 비워 신규 ACTIVE와 유니크 충돌 방지. */
    public void supersede() {
        this.status = STATUS_SUPERSEDED;
        this.activeUserId = null;
    }

    public void addItem(SalaryPlanItem item) {
        items.add(item);
        item.assignPlan(this);
    }
}
