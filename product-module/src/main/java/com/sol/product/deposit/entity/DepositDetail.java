package com.sol.product.deposit.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "deposit_detail")
@Getter
@NoArgsConstructor
public class DepositDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_detail_id")
    private Long depositDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "maturity_months")
    private Integer maturityMonths;

    @Column(name = "subscription_limit", precision = 15, scale = 0)
    private BigDecimal subscriptionLimit;

    @Column(name = "deposit_insurance")
    private Boolean depositInsurance;
}
