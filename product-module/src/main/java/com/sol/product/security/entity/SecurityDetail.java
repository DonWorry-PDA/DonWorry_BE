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
@Table(name = "증권상품정보")
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

    @Column(name = "종목코드", length = 20)
    private String tickerCode;

    @Column(name = "상장시장", length = 20)
    private String listedMarket;

    // STOCK(주식) / BOND(채권)
    @Column(name = "세부유형", length = 20, nullable = false)
    private String detailType;

    // 채권 전용
    @Column(name = "신용등급", length = 10)
    private String creditRating;

    @Column(name = "만기일")
    private LocalDate maturityDate;

    @Column(name = "표면금리", precision = 5, scale = 2)
    private BigDecimal couponRate;

    // 주식 전용
    @Column(name = "배당수익률", precision = 5, scale = 2)
    private BigDecimal dividendYield;

    @Column(name = "섹터", length = 50)
    private String sector;

    @Column(name = "담을수있는계좌", length = 100)
    private String availableAccountTypes;
}
