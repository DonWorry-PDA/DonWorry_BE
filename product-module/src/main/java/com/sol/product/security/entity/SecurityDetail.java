package com.sol.product.security.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(name = "세부유형", length = 30)
    private String detailType;

    @Column(name = "수수료", precision = 5, scale = 2)
    private BigDecimal fee;

    @Column(name = "신용등급", length = 10)
    private String creditRating;

    @Column(name = "만기일")
    private LocalDate maturityDate;

    @Column(name = "담을수있는계좌", length = 50)
    private String availableAccountTypes;
}
