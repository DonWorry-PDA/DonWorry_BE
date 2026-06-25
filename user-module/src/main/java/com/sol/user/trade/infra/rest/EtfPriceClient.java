package com.sol.user.trade.infra.rest;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EtfPriceClient {

    private final RestClient productRestClient;

    public EtfPriceClient(RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public long getCurrentPrice(Long productId) {
        try {
            EtfCurrentPriceApiResponse response = productRestClient.get()
                    .uri("/api/product/etfs/{productId}/price", productId)
                    .retrieve()
                    .body(EtfCurrentPriceApiResponse.class);
            if (response == null || response.data() == null) {
                throw new BaseException(ErrorCode.PRICE_UNAVAILABLE);
            }
            return response.data();
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.PRICE_UNAVAILABLE);
        }
    }
}
