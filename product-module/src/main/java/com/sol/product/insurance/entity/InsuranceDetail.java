package com.sol.product.insurance.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "보험형")
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

    @Column(name = "보험유형", length = 30)
    private String insuranceType;

    @Column(name = "보장금액")
    private Integer coverageAmount;

    @Column(name = "자기부담금비율")
    private Integer selfPayRatio;
}
