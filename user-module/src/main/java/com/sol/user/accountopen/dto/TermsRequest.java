package com.sol.user.accountopen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class TermsRequest {

    @NotNull(message = "약관 동의 목록은 필수입니다.")
    @Size(min = 1, message = "약관 동의 목록이 비어있습니다.")
    private List<@NotBlank(message = "약관 ID는 빈 값일 수 없습니다.") String> agreedTermIds;
}
