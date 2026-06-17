package com.sol.product.security.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 주식/채권 전용 상세 정보 테이블.
 * ETF는 EtfDetail, 펀드는 FundDetail로 분리되어 관리됩니다.
 */
@Entity
@Table(name = "security_detail")
@Getter
@NoArgsConstructor
public class SecurityDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "security_detail_id")
    private Long securityDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "ticker_code", length = 20)
    private String tickerCode;

    @Column(name = "listed_market", length = 20)
    private String listedMarket;

    // STOCK(주식) / BOND(채권)
    @Column(name = "detail_type", length = 20, nullable = false)
    private String detailType;

    // 채권 전용
    @Column(name = "credit_rating", length = 10)
    private String creditRating;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "coupon_rate", precision = 5, scale = 2)
    private BigDecimal couponRate;

    // 주식 전용
    @Column(name = "dividend_yield", precision = 5, scale = 2)
    private BigDecimal dividendYield;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "available_account_types", length = 100)
    private String availableAccountTypes;
}
