package com.sol.common.response;

import com.sol.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    @Builder
    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .code("SUCCESS")
                .message("성공")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .data(null)
                .build();
    }
}
