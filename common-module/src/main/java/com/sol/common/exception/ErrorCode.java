package com.sol.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다."),
    PRODUCT_POOL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_002", "상품 풀을 일시적으로 조회할 수 없습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "알림을 찾을 수 없습니다."),

    // Auth
    AUTH_001(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증에 실패했습니다"),
    AUTH_002(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 토큰입니다"),
    AUTH_003(HttpStatus.UNAUTHORIZED, "AUTH_003", "토큰이 만료되었습니다"),

    // Survey
    SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_001", "설문 답변이 없습니다."),

    // OTP
    OTP_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_REQUESTS", "단시간 재발송 제한을 초과했습니다."),
    INVALID_PHONE(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "유효하지 않은 전화번호입니다."),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "OTP_INVALID", "인증번호가 일치하지 않습니다."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "인증번호가 만료되었습니다."),
    OTP_MAX_ATTEMPTS(HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS", "인증번호 입력 횟수를 초과했습니다."),
    SMS_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SMS_SEND_FAILED", "SMS 발송에 실패했습니다."),

    // Account Open
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "이미 계좌를 보유하고 있습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS_NOT_AGREED", "필수 약관에 동의하지 않았습니다."),
    OTP_NOT_VERIFIED(HttpStatus.FORBIDDEN, "OTP_NOT_VERIFIED", "본인 인증이 완료되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
