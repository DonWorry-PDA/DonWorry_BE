package com.sol.user.accountopen.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_TTL_MINUTES = 3;
    private static final int RATE_LIMIT_TTL_MINUTES = 5;
    private static final int RATE_LIMIT_MAX = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int VERIFIED_TTL_MINUTES = 30;

    private static final String KEY_CODE = "otp:code:";
    private static final String KEY_ATTEMPTS = "otp:attempts:";
    private static final String KEY_RATE = "otp:rate:";
    private static final String KEY_VERIFIED = "otp:verified:";

    private static final String PHONE_REGEX = "^010-\\d{4}-\\d{4}$";

    private static final SecureRandom secureRandom = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;
    private final SmsService smsService;

    public void sendOtp(String phone, Long userId) {
        if (!phone.matches(PHONE_REGEX)) {
            throw new BaseException(ErrorCode.INVALID_PHONE);
        }

        String userPhone = userId + ":" + phone;
        String rateKey = KEY_RATE + userPhone;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, RATE_LIMIT_TTL_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new BaseException(ErrorCode.OTP_TOO_MANY_REQUESTS);
        }

        String otp = generateOtp();
        redisTemplate.opsForValue().set(KEY_CODE + userPhone, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.delete(KEY_ATTEMPTS + userPhone);

        smsService.sendOtp(phone, otp);
    }

    public void verifyOtp(String phone, String inputOtp, Long userId) {
        String userPhone = userId + ":" + phone;
        String attemptsKey = KEY_ATTEMPTS + userPhone;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, OTP_TTL_MINUTES + 2, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            throw new BaseException(ErrorCode.OTP_MAX_ATTEMPTS);
        }

        String stored = redisTemplate.opsForValue().get(KEY_CODE + userPhone);
        if (stored == null) {
            throw new BaseException(ErrorCode.OTP_EXPIRED);
        }
        if (!stored.equals(inputOtp)) {
            throw new BaseException(ErrorCode.OTP_INVALID);
        }

        redisTemplate.delete(KEY_CODE + userPhone);
        redisTemplate.delete(attemptsKey);
        redisTemplate.opsForValue().set(KEY_VERIFIED + userId, "Y", VERIFIED_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public boolean isVerified(Long userId) {
        return "Y".equals(redisTemplate.opsForValue().get(KEY_VERIFIED + userId));
    }

    public void clearVerified(Long userId) {
        redisTemplate.delete(KEY_VERIFIED + userId);
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
