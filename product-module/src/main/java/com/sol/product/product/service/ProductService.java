package com.sol.product.product.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.deposit.repository.DepositDetailRepository;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import com.sol.product.pensionsaving.repository.PensionSavingDetailRepository;
import com.sol.product.product.dto.DepositDetailBatchItem;
import com.sol.product.product.dto.ProductBatchItem;
import com.sol.product.product.dto.ProductDetailResponse;
import com.sol.product.product.entity.FinancialProduct;
import com.sol.product.product.mapper.ProductDetailMapper;
import com.sol.product.product.repository.FinancialProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final String TYPE_ETF = "ETF";
    private static final String TYPE_DEPOSIT = "DEPOSIT";
    private static final String TYPE_PENSION_SAVING = "PENSION_SAVING";

    private final FinancialProductRepository financialProductRepository;
    private final EtfDetailRepository etfDetailRepository;
    private final DepositDetailRepository depositDetailRepository;
    private final PensionSavingDetailRepository pensionSavingDetailRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final ProductDetailMapper productDetailMapper;

    public List<ProductBatchItem> getProductsByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<FinancialProduct> products = financialProductRepository.findAllByProductIdIn(productIds);

        List<Long> etfProductIds = products.stream()
                .filter(p -> TYPE_ETF.equals(p.getProductType()))
                .map(FinancialProduct::getProductId)
                .toList();
        Map<Long, String> tickerByProductId = etfProductIds.isEmpty() ? Map.of()
                : etfDetailRepository.findAllByProductProductIdIn(etfProductIds).stream()
                        .collect(Collectors.toMap(
                                e -> e.getProduct().getProductId(),
                                EtfDetail::getTickerCode
                        ));

        if (etfProductIds.stream().anyMatch(id -> !tickerByProductId.containsKey(id))) {
            throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return products.stream()
                .map(p -> new ProductBatchItem(
                        p.getProductId(),
                        p.getProductName(),
                        p.getProductType(),
                        tickerByProductId.get(p.getProductId())
                ))
                .toList();
    }

    public List<DepositDetailBatchItem> getDepositDetailsByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return depositDetailRepository.findByProductProductIdIn(productIds).stream()
                .map(d -> new DepositDetailBatchItem(
                        d.getProduct().getProductId(),
                        d.getInterestRate(),
                        d.getMaturityMonths()))
                .toList();
    }

    public List<Long> getAllDepositProductIds() {
        return depositDetailRepository.findAllProductIds();
    }

    public ProductDetailResponse getProductDetail(Long productId) {
        FinancialProduct product = financialProductRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));

        return switch (product.getProductType()) {
            case TYPE_ETF -> {
                EtfDetail etf = etfDetailRepository.findByProductProductId(productId)
                        .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
                DailyPrice latestPrice = dailyPriceRepository
                        .findTopByProductProductIdOrderByPriceDateDesc(productId)
                        .orElse(null);
                yield productDetailMapper.toEtfResponse(product, etf, latestPrice);
            }
            case TYPE_DEPOSIT -> {
                var deposit = depositDetailRepository.findByProductProductId(productId)
                        .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
                yield productDetailMapper.toDepositResponse(product, deposit);
            }
            case TYPE_PENSION_SAVING -> {
                var pensionSaving = pensionSavingDetailRepository.findByProductId(productId)
                        .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
                yield productDetailMapper.toPensionSavingResponse(product, pensionSaving);
            }
            default -> throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);
        };
    }
}
