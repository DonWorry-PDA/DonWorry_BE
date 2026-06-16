package com.sol.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final String code;

    public BaseException(String code, String message) {
        super(message);
        this.code = code;
    }
}
