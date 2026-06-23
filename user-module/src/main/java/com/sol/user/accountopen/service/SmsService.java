package com.sol.user.accountopen.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    @Value("${solapi.api-key}")
    private String apiKey;

    @Value("${solapi.api-secret}")
    private String apiSecret;

    @Value("${solapi.from-number}")
    private String fromNumber;

    private DefaultMessageService messageService;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.solapi.com");
    }

    public void sendOtp(String phone, String otp) {
        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(phone.replace("-", ""));
        message.setText("[신한 은퇴솔루션] 인증번호는 [" + otp + "] 입니다. 3분 내에 입력해 주세요.");
        try {
            messageService.send(message);
        } catch (Exception e) {
            log.error("SMS 발송 실패 - phone: {}", maskPhone(phone), e);
            throw new BaseException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    private String maskPhone(String phone) {
        return phone.replaceAll("(\\d{3}-)(\\d{4})(-\\d{4})", "$1****$3");
    }
}
