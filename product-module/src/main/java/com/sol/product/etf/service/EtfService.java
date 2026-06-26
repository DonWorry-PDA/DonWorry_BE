package com.sol.product.etf.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dividend.entity.DividendHistory;
import com.sol.product.dividend.repository.DividendHistoryRepository;
import com.sol.product.etf.dto.EtfDocumentResponse;
import com.sol.product.etf.dto.EtfMonthlyDividendItem;
import com.sol.product.etf.dto.EtfPoolItem;
import com.sol.product.etf.dto.EtfResponse;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public EtfDocumentResponse getEtfDocuments(String tickerCode) {
        return etfDetailRepository.findByTickerCode(tickerCode)
                .map(EtfDocumentResponse::from)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 넘겨받은 ticker들의 ETF 상세를 벌크 조회한다.
     * 미존재 ticker는 결과에서 빠질 뿐 예외를 던지지 않는다(부분 반환).
     */
    public List<EtfPoolItem> getPoolByTickers(List<String> tickerCodes) {
        if (tickerCodes == null || tickerCodes.isEmpty()) {
            return List.of();
        }
        return etfDetailRepository.findAllByTickerCodeIn(tickerCodes).stream()
                .map(EtfPoolItem::from)
                .toList();
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

    public List<EtfMonthlyDividendItem> getMonthlyDividends(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = productIds.stream().distinct().toList();
        Map<Long, DividendHistory> latestDividendMap = dividendHistoryRepository
                .findLatestByProductIds(distinctIds).stream()
                .collect(Collectors.toMap(
                        d -> d.getProduct().getProductId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return etfDetailRepository.findAllByProductProductIdIn(distinctIds).stream()
                .filter(e -> e.getDistributionIntervalMonths() != null && e.getDistributionIntervalMonths() > 0)
                .map(e -> {
                    Long productId = e.getProduct().getProductId();
                    DividendHistory latest = latestDividendMap.get(productId);
                    if (latest == null || latest.getAmountPerUnit() == null) return null;
                    BigDecimal monthlyPerUnit = latest.getAmountPerUnit()
                            .divide(BigDecimal.valueOf(e.getDistributionIntervalMonths()), 10, RoundingMode.HALF_UP);
                    return new EtfMonthlyDividendItem(productId, monthlyPerUnit);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private EtfResponse toResponse(EtfDetail etfDetail) {
        return dividendHistoryRepository
                .findTopByProductProductIdOrderByPaymentDateDesc(etfDetail.getProduct().getProductId())
                .map(latestDividend -> EtfResponse.from(etfDetail, latestDividend))
                .orElseGet(() -> EtfResponse.from(etfDetail));
    }
}
