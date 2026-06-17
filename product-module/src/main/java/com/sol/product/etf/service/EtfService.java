package com.sol.product.etf.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.etf.dto.EtfResponse;
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

    public List<EtfResponse> getAllEtfs() {
        return etfDetailRepository.findAll().stream()
                .map(EtfResponse::from)
                .toList();
    }

    public List<EtfResponse> getEtfsByAssetManager(String assetManager) {
        return etfDetailRepository.findAllByAssetManager(assetManager).stream()
                .map(EtfResponse::from)
                .toList();
    }

    public EtfResponse getEtfByTickerCode(String tickerCode) {
        return etfDetailRepository.findByTickerCode(tickerCode)
                .map(EtfResponse::from)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public EtfResponse getEtfByProductId(Long productId) {
        return etfDetailRepository.findByProductProductId(productId)
                .map(EtfResponse::from)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
