package com.sol.product.fund.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "펀드상품정보")
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

    @Column(name = "펀드코드", length = 30)
    private String fundCode;

    @Column(name = "위험등급", length = 30)
    private String riskGrade;

    @Column(name = "펀드유형", length = 30)
    private String fundType;

    @Column(name = "설정일")
    private LocalDate inceptionDate;

    @Column(name = "순자산", precision = 15, scale = 2)
    private BigDecimal netAsset;

    @Column(name = "총보수", precision = 5, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "수익률_1년", precision = 7, scale = 2)
    private BigDecimal return1Y;

    @Column(name = "수익률_3년", precision = 7, scale = 2)
    private BigDecimal return3Y;
}