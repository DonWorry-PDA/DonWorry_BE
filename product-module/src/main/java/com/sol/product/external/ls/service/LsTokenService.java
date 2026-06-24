package com.sol.product.external.ls.service;

import com.sol.product.external.ls.LsProperties;
import com.sol.product.external.ls.dto.LsTokenResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LsTokenService {

    private static final String REDIS_TOKEN_KEY = "ls:access_token";
    private static final long TOKEN_TTL_HOURS = 23;

    private final LsProperties lsProperties;
    private final StringRedisTemplate redisTemplate;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public String getToken() {
        String cached = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        return issueToken();
    }

    public void clearToken() {
        redisTemplate.delete(REDIS_TOKEN_KEY);
        log.info("LS 토큰 캐시 삭제");
    }

    private String issueToken() {
        String body = "grant_type=client_credentials"
                + "&appkey=" + lsProperties.getAppKey()
                + "&appsecretkey=" + lsProperties.getAppSecret()
                + "&scope=oob";

        LsTokenResponse response = restClient.post()
                .uri(lsProperties.getBaseUrl() + "/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .body(LsTokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("LS증권 토큰 발급 실패: 빈 응답");
        }

        redisTemplate.opsForValue().set(
                REDIS_TOKEN_KEY,
                response.accessToken(),
                TOKEN_TTL_HOURS,
                TimeUnit.HOURS
        );

        log.info("LS 토큰 발급 완료");
        return response.accessToken();
    }
}
