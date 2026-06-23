package com.sol.user.accountopen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AccountOpenRequest {

    @NotNull(message = "약관 동의 목록은 필수입니다.")
    @Size(min = 1, message = "약관 동의 목록이 비어있습니다.")
    private List<@NotBlank(message = "약관 ID는 빈 값일 수 없습니다.") String> agreedTermIds;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다. (예: 010-1234-5678)")
    private String phone;
}
