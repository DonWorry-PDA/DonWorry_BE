package com.sol.product.etf.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dividend.repository.DividendHistoryRepository;
import com.sol.product.etf.dto.EtfResponse;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtfService {

    private final EtfDetailRepository etfDetailRepository;
    private final DividendHistoryRepository dividendHistoryRepository;

    public List<EtfResponse> getAllEtfs() {
        return etfDetailRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EtfResponse> getEtfsByAssetManager(String assetManager) {
        return etfDetailRepository.findAllByAssetManager(assetManager).stream()
                .map(this::toResponse)
                .toList();
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

    private EtfResponse toResponse(EtfDetail etfDetail) {
        return dividendHistoryRepository
                .findTopByProductProductIdOrderByPaymentDateDesc(etfDetail.getProduct().getProductId())
                .map(latestDividend -> EtfResponse.from(etfDetail, latestDividend))
                .orElseGet(() -> EtfResponse.from(etfDetail));
    }
}
