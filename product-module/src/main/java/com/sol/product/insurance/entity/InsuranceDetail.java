package com.sol.product.insurance.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "insurance_detail")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insurance_detail_id")
    private Long insuranceDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "insurance_type", length = 50)
    private String insuranceType;

    @Column(name = "coverage_amount")
    private Long coverageAmount;

    @Column(name = "self_pay_ratio")
    private Integer selfPayRatio;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(name = "sales_channel", length = 30)
    private String salesChannel;

    @Column(name = "sales_start_date")
    private LocalDate salesStartDate;

    @Column(name = "sales_end_date")
    private LocalDate salesEndDate;

    @Column(name = "summary_pdf_url", columnDefinition = "TEXT")
    private String summaryPdfUrl;

    @Column(name = "business_method_pdf_url", columnDefinition = "TEXT")
    private String businessMethodPdfUrl;

    @Column(name = "terms_pdf_url", columnDefinition = "TEXT")
    private String termsPdfUrl;

}