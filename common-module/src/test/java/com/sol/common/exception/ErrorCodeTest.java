package com.sol.common.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void authErrorCodes_exist() {
        assertThat(ErrorCode.AUTH_001.getCode()).isEqualTo("AUTH_001");
        assertThat(ErrorCode.AUTH_002.getCode()).isEqualTo("AUTH_002");
        assertThat(ErrorCode.AUTH_003.getCode()).isEqualTo("AUTH_003");
    }

    @Test
    void auth001_hasUnauthorizedStatus() {
        assertThat(ErrorCode.AUTH_001.getHttpStatus().value()).isEqualTo(401);
    }
}
