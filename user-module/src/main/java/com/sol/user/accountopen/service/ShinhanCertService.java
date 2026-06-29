package com.sol.user.accountopen.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ShinhanCertService {

    private static final int VERIFIED_TTL_MINUTES = 30;
    private static final String KEY_VERIFIED = "shinhan-cert:verified:";

    private final RedisTemplate<String, String> redisTemplate;

    public void verify(Long userId) {
        redisTemplate.opsForValue().set(KEY_VERIFIED + userId, "Y", VERIFIED_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public boolean isVerified(Long userId) {
        return "Y".equals(redisTemplate.opsForValue().get(KEY_VERIFIED + userId));
    }

    public void clearVerified(Long userId) {
        redisTemplate.delete(KEY_VERIFIED + userId);
    }
}
