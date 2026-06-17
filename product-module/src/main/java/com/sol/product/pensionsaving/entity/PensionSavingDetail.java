package com.sol.product.pensionsaving.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "연금저축상세")
@Getter
@NoArgsConstructor
public class PensionSavingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pension_saving_detail_id")
    private Long pensionSavingDetailId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "연금저축종류", length = 20)
    private String pensionSavingType;

    @Column(name = "세부유형", length = 30)
    private String detailType;

    @Column(name = "평균수익률", precision = 5, scale = 2)
    private BigDecimal avgReturnRate;

    @Column(name = "수익률1년", precision = 5, scale = 2)
    private BigDecimal returnRate1Year;

    @Column(name = "수익률2년", precision = 5, scale = 2)
    private BigDecimal returnRate2Year;

    @Column(name = "수익률3년", precision = 5, scale = 2)
    private BigDecimal returnRate3Year;

    @Column(name = "가입방법", length = 50)
    private String subscriptionMethod;

    @Column(name = "제공기관명", length = 100)
    private String providerName;
}
