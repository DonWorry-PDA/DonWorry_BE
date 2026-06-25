package com.sol.product.etf.realtime;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.etf.entity.EtfDetail;
import com.sol.product.etf.repository.EtfDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtfRealtimeService {

    private final EtfDetailRepository etfDetailRepository;
    private final EtfRealtimeCache etfRealtimeCache;
    private final DailyPriceRepository dailyPriceRepository;

    public EtfRealtimeResponse getRealtime(String ticker) {
        EtfDetail etf = etfDetailRepository.findWithProductByTickerCode(ticker)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
        EtfRealtimeResponse response = etfRealtimeCache.get(ticker, etf.getProduct().getProductName());
        if (response == null) {
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
        return response;
    }

    public long getCurrentPriceByProductId(Long productId) {
        EtfDetail etf = etfDetailRepository.findByProductProductId(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));

        Map<String, String> raw = etfRealtimeCache.getRaw(etf.getTickerCode());
        if (!raw.isEmpty() && raw.containsKey("price") && !raw.get("price").isBlank()) {
            return Long.parseLong(raw.get("price").trim());
        }

        return dailyPriceRepository.findTopByProductProductIdOrderByPriceDateDesc(productId)
                .map(dp -> dp.getClosingPrice().longValue())
                .orElseThrow(() -> new BaseException(ErrorCode.PRICE_UNAVAILABLE));
    }

    public List<EtfRealtimeResponse> getAllRealtime() {
        Map<String, String> tickerToName = etfDetailRepository
                .findAllByTickerCodeIn(EtfTickerWhitelist.TICKERS)
                .stream()
                .collect(Collectors.toMap(
                        EtfDetail::getTickerCode,
                        e -> e.getProduct().getProductName()
                ));

        return EtfTickerWhitelist.TICKERS.stream()
                .map(ticker -> etfRealtimeCache.get(ticker, tickerToName.getOrDefault(ticker, ticker)))
                .filter(Objects::nonNull)
                .toList();
    }
}
