package com.sol.product.etf.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dividend.entity.DividendHistory;
import com.sol.product.dividend.repository.DividendHistoryRepository;
import com.sol.product.etf.dto.EtfResponse;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtfService {

    private final EtfDetailRepository etfDetailRepository;
    private final DividendHistoryRepository dividendHistoryRepository;

    public List<EtfResponse> getAllEtfs() {
        return toResponses(etfDetailRepository.findAll());
    }

    public List<EtfResponse> getEtfsByAssetManager(String assetManager) {
        return toResponses(etfDetailRepository.findAllByAssetManager(assetManager));
    }

    public EtfResponse getEtfByTickerCode(String tickerCode) {
        return etfDetailRepository.findByTickerCode(tickerCode)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public EtfResponse getEtfByProductId(Long productId) {
        return etfDetailRepository.findByProductProductId(productId)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private List<EtfResponse> toResponses(List<EtfDetail> etfDetails) {
        if (etfDetails.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = etfDetails.stream()
                .map(etfDetail -> etfDetail.getProduct().getProductId())
                .toList();

        Map<Long, DividendHistory> latestDividendByProductId = dividendHistoryRepository
                .findLatestByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                        dividendHistory -> dividendHistory.getProduct().getProductId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return etfDetails.stream()
                .map(etfDetail -> EtfResponse.from(
                        etfDetail,
                        latestDividendByProductId.get(etfDetail.getProduct().getProductId())
                ))
                .toList();
    }

    private EtfResponse toResponse(EtfDetail etfDetail) {
        return dividendHistoryRepository
                .findTopByProductProductIdOrderByPaymentDateDesc(etfDetail.getProduct().getProductId())
                .map(latestDividend -> EtfResponse.from(etfDetail, latestDividend))
                .orElseGet(() -> EtfResponse.from(etfDetail));
    }
}
