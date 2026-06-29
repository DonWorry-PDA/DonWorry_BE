package com.sol.product.stock.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StockRealtimeCache {

    private static final String KEY_PREFIX = "stock:rt:";

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
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (String) e.getValue()
                ));
    }
}
