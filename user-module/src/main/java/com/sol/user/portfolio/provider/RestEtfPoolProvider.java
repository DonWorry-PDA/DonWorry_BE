package com.sol.user.portfolio.provider;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.infra.rest.EtfPoolApiResponse;
import com.sol.user.portfolio.infra.rest.EtfPoolItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * REST(A1) 구현 — product-module 풀조회 API 호출. 풀은 26종 고정이라 lazy 1회 로드 후 캐시.
 * 실패는 PRODUCT_POOL_UNAVAILABLE(503)로 변환(무fallback). 실패를 캐시하지 않아 다음 호출에 재시도된다.
 */
@Component
public class RestEtfPoolProvider implements EtfPoolProvider {

    private final RestClient productRestClient;

    private volatile List<EtfInfo> cache;

    public RestEtfPoolProvider(RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    @Override
    public List<EtfInfo> getPool() {
        List<EtfInfo> local = cache;
        if (local == null) {
            synchronized (this) {
                local = cache;
                if (local == null) {
                    local = load();
                    cache = local;
                }
            }
        }
        return local;
    }

    private List<EtfInfo> load() {
        try {
            EtfPoolApiResponse response = productRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/product/etfs/pool")
                            .queryParam("tickers", PortfolioConstants.WHITELIST)
                            .build())
                    .retrieve()
                    .body(EtfPoolApiResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
            }
            return response.data().stream()
                    .map(this::toEtfInfo)
                    .toList();
        } catch (BaseException e) {
            throw e; // 이미 변환된 도메인 예외는 그대로 전파
        } catch (Exception e) {
            // REST 호출·역직렬화·매핑 어디서 터지든 일관되게 503으로 변환
            throw new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE);
        }
    }

    /** product 응답(상품 사실)에 PortfolioConstants 매핑(role·currency)을 합성. */
    private EtfInfo toEtfInfo(EtfPoolItem item) {
        int riskGrade = item.riskGrade() != null
                ? item.riskGrade()
                : PortfolioConstants.RISK_GRADE.getOrDefault(item.ticker(), 0);
        return new EtfInfo(
                item.ticker(),
                item.productName(),
                riskGrade,
                item.annualDividendRate(),
                item.distributionCycle(),
                PortfolioConstants.roleOf(item.ticker()),
                PortfolioConstants.currencyOf(item.ticker())
        );
    }
}
