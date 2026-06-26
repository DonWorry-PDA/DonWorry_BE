package com.sol.user.monthlysalary.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 확정 plan의 개별 보유 종목 스냅샷. {@code target_amount}는 gross 목표(차감 전 전체 목표 배분액) —
 * 운용현황 진행률 분모로 쓰이므로 net(차감액) 저장 금지(결정 3).
 */
@Entity
@Table(name = "salary_plan_item")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SalaryPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SalaryPlan salaryPlan;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 표시명 스냅샷 — 상품 카탈로그 공백/장애 시에도 운용현황 화면이 안 깨지게(결정 6). */
    @Column(name = "product_name", length = 100)
    private String productName;

    /** RISK / SAFE / SHORT_TERM (운용현황 표시). */
    @Column(name = "bucket_role", length = 20)
    private String bucketRole;

    /** BROKERAGE / IRP (향후 IRP 확장 여지 — 현재는 BROKERAGE 한정). */
    @Column(name = "account_type", length = 20)
    private String accountType;

    /** 버킷 내 비중. */
    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    /** gross 목표 배분액(차감 전) — 결정 3. */
    @Column(name = "target_amount", precision = 15, scale = 0)
    private BigDecimal targetAmount;

    /** 종목별 월 기여(선택 — 미제공 시 null). */
    @Column(name = "product_contribution", precision = 15, scale = 0)
    private BigDecimal productContribution;

    void assignPlan(SalaryPlan salaryPlan) {
        this.salaryPlan = salaryPlan;
    }
}
