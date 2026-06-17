package com.sol.product.fund.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fund_detail")
@Getter
@NoArgsConstructor
public class FundDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fund_detail_id")
    private Long fundDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "fund_code", length = 30)
    private String fundCode;

    @Column(name = "risk_grade", length = 30)
    private String riskGrade;

    @Column(name = "fund_type", length = 30)
    private String fundType;

    @Column(name = "inception_date")
    private LocalDate inceptionDate;

    @Column(name = "net_asset", precision = 15, scale = 2)
    private BigDecimal netAsset;

    @Column(name = "total_fee", precision = 5, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "return_1y", precision = 7, scale = 2)
    private BigDecimal return1Y;

    @Column(name = "return_3y", precision = 7, scale = 2)
    private BigDecimal return3Y;
}