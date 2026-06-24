package com.sol.product.etf.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EtfRealtimeCache {

    private static final String KEY_PREFIX = "etf:rt:";

    private final StringRedisTemplate redisTemplate;

    public void save(String ticker, String price, String change, String drate, String sign) {
        String key = KEY_PREFIX + ticker;
        redisTemplate.opsForHash().put(key, "price", price.trim());
        redisTemplate.opsForHash().put(key, "change", change.trim());
        redisTemplate.opsForHash().put(key, "drate", drate.trim());
        redisTemplate.opsForHash().put(key, "sign", sign != null ? sign.trim() : "3");
    }

    public Map<String, String> getRaw(String ticker) {
        String key = KEY_PREFIX + ticker;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) return Map.of();
        return data.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (String) e.getValue()
                ));
    }

    public EtfRealtimeResponse get(String ticker, String productName) {
        String key = KEY_PREFIX + ticker;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) return null;

        String price = (String) data.get("price");
        String change = (String) data.get("change");
        String drate = (String) data.get("drate");
        String sign = (String) data.getOrDefault("sign", "3");

        if (price == null || price.isBlank()) return null;

        return new EtfRealtimeResponse(
                ticker,
                productName,
                Long.parseLong(price),
                Long.parseLong(change),
                new BigDecimal(drate),
                sign
        );
    }
}
