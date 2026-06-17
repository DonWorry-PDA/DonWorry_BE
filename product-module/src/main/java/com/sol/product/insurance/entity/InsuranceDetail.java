package com.sol.product.insurance.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "insurance_detail")
@Getter
@NoArgsConstructor
public class InsuranceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insurance_detail_id")
    private Long insuranceDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "insurance_type", length = 30)
    private String insuranceType;

    @Column(name = "coverage_amount")
    private Integer coverageAmount;

    @Column(name = "self_pay_ratio")
    private Integer selfPayRatio;
}
