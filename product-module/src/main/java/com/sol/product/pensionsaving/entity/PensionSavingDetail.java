package com.sol.product.pensionsaving.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "pension_saving_detail")
@Getter
@NoArgsConstructor
public class PensionSavingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pension_saving_detail_id")
    private Long pensionSavingDetailId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "pension_saving_type", length = 20)
    private String pensionSavingType;

    @Column(name = "detail_type", length = 30)
    private String detailType;

    @Column(name = "avg_return_rate", precision = 5, scale = 2)
    private BigDecimal avgReturnRate;

    @Column(name = "return_rate_1y", precision = 5, scale = 2)
    private BigDecimal returnRate1Year;

    @Column(name = "return_rate_2y", precision = 5, scale = 2)
    private BigDecimal returnRate2Year;

    @Column(name = "return_rate_3y", precision = 5, scale = 2)
    private BigDecimal returnRate3Year;

    @Column(name = "subscription_method", length = 50)
    private String subscriptionMethod;

    @Column(name = "provider_name", length = 100)
    private String providerName;
}
