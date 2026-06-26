package com.sol.product.product.mapper;

import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.deposit.entity.DepositDetail;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.pensionsaving.entity.PensionSavingDetail;
import com.sol.product.product.dto.DepositDetailDto;
import com.sol.product.product.dto.EtfDetailDto;
import com.sol.product.product.dto.PensionSavingDetailDto;
import com.sol.product.product.dto.ProductDetailResponse;
import com.sol.product.product.entity.FinancialProduct;
import org.springframework.stereotype.Component;

@Component
public class ProductDetailMapper {

    public ProductDetailResponse toEtfResponse(FinancialProduct product, EtfDetail etf, DailyPrice latestPrice) {
        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productType(product.getProductType())
                .etfDetail(toEtfDetailDto(etf, latestPrice))
                .build();
    }

    public ProductDetailResponse toDepositResponse(FinancialProduct product, DepositDetail deposit) {
        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productType(product.getProductType())
                .depositDetail(toDepositDetailDto(deposit))
                .build();
    }

    public ProductDetailResponse toPensionSavingResponse(FinancialProduct product, PensionSavingDetail pensionSaving) {
        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productType(product.getProductType())
                .pensionSavingDetail(toPensionSavingDetailDto(pensionSaving))
                .build();
    }

    private EtfDetailDto toEtfDetailDto(EtfDetail etf, DailyPrice latestPrice) {
        EtfDetailDto.EtfDetailDtoBuilder builder = EtfDetailDto.builder()
                .tickerCode(etf.getTickerCode())
                .assetManager(etf.getAssetManager())
                .brandName(etf.getBrandName())
                .riskGrade(etf.getRiskGrade())
                .annualDividendRate(etf.getAnnualDividendRate())
                .latestDividendRate(etf.getLatestDividendRate())
                .distributionCycle(etf.getDistributionCycle())
                .distributionIntervalMonths(etf.getDistributionIntervalMonths())
                .high52w(etf.getHigh52w())
                .low52w(etf.getLow52w())
                .retirementPensionLimit(etf.getRetirementPensionLimit())
                .personalPensionAvailable(etf.getPersonalPensionAvailable())
                .prospectusUrl(etf.getProspectusUrl())
                .simplifiedUrl(etf.getSimplifiedUrl())
                .fundRulesUrl(etf.getFundRulesUrl());

        if (latestPrice != null) {
            builder.closingPrice(latestPrice.getClosingPrice())
                    .priceChange(latestPrice.getPriceChange())
                    .changeRate(latestPrice.getChangeRate())
                    .nav(latestPrice.getNav())
                    .netAssetTotal(latestPrice.getNetAssetTotal());
        }

        return builder.build();
    }

    private DepositDetailDto toDepositDetailDto(DepositDetail deposit) {
        return DepositDetailDto.builder()
                .interestRate(deposit.getInterestRate())
                .maturityMonths(deposit.getMaturityMonths())
                .subscriptionLimit(deposit.getSubscriptionLimit())
                .depositInsurance(deposit.getDepositInsurance())
                .build();
    }

    private PensionSavingDetailDto toPensionSavingDetailDto(PensionSavingDetail pensionSaving) {
        return PensionSavingDetailDto.builder()
                .pensionSavingType(pensionSaving.getPensionSavingType())
                .detailType(pensionSaving.getDetailType())
                .avgReturnRate(pensionSaving.getAvgReturnRate())
                .returnRate1Year(pensionSaving.getReturnRate1Year())
                .returnRate2Year(pensionSaving.getReturnRate2Year())
                .returnRate3Year(pensionSaving.getReturnRate3Year())
                .subscriptionMethod(pensionSaving.getSubscriptionMethod())
                .providerName(pensionSaving.getProviderName())
                .build();
    }
}
