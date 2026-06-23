package com.sol.user.accountopen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OtpVerifyRequest {

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phone;

    @NotBlank(message = "인증번호는 필수입니다.")
    private String otp;
}
