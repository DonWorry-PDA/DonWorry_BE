package com.sol.user.accountopen.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.accountopen.dto.AccountOpenRequest;
import com.sol.user.accountopen.dto.AccountOpenResponse;
import com.sol.user.accountopen.dto.IdentityResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountOpenServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AccountOpenService accountOpenService;

    private static final Long USER_ID = 1L;
    private static final List<String> VALID_TERMS =
            List.of("account", "deposit", "account-privacy", "account-third-party");

    // ── validateTerms ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("필수 약관 4개 모두 포함하면 예외 없이 통과한다")
    void validateTerms_allRequired_noException() {
        assertThatNoException().isThrownBy(
                () -> accountOpenService.validateTerms(VALID_TERMS));
    }

    @Test
    @DisplayName("필수 약관 중 하나라도 빠지면 TERMS_NOT_AGREED 예외를 던진다")
    void validateTerms_missing_throwsTermsNotAgreed() {
        List<String> partial = List.of("account", "deposit");

        assertThatThrownBy(() -> accountOpenService.validateTerms(partial))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERMS_NOT_AGREED);
    }

    // ── getIdentity ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("주민번호 앞 6자리 외 나머지는 마스킹되어 반환된다")
    void getIdentity_masksIdNumber() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("홍길동");
        when(user.getIdNumber()).thenReturn("9010101234567");
        when(user.getPhone()).thenReturn("010-1234-5678");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        IdentityResponse response = accountOpenService.getIdentity(USER_ID);

        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getIdNumberMasked()).isEqualTo("901010-●●●●●●●");
        assertThat(response.getPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("존재하지 않는 userId는 USER_NOT_FOUND 예외를 던진다")
    void getIdentity_userNotFound_throwsUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountOpenService.getIdentity(USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── openAccount ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OTP 인증 완료 후 계좌 개설 시 계좌번호와 개설일을 반환한다")
    void openAccount_verified_returnsAccountInfo() {
        User user = mock(User.class);
        AccountOpenRequest request = mock(AccountOpenRequest.class);
        when(request.getAgreedTermIds()).thenReturn(VALID_TERMS);
        when(otpService.isVerified(USER_ID)).thenReturn(true);
        when(accountRepository.existsByUserUserIdAndAccountType(USER_ID, Account.TYPE_DON_WORRY)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        AccountOpenResponse response = accountOpenService.openAccount(USER_ID, request);

        assertThat(response.getAccountNumber()).matches("110-\\d{3}-\\d{6}");
        assertThat(response.getOpenedAt()).isNotNull();
        verify(accountRepository).save(any(Account.class));
        verify(otpService).clearVerified(USER_ID);
    }

    @Test
    @DisplayName("OTP 미인증 시 OTP_NOT_VERIFIED 예외를 던진다")
    void openAccount_notVerified_throwsOtpNotVerified() {
        AccountOpenRequest request = mock(AccountOpenRequest.class);
        when(request.getAgreedTermIds()).thenReturn(VALID_TERMS);
        when(otpService.isVerified(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> accountOpenService.openAccount(USER_ID, request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_NOT_VERIFIED);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 계좌가 존재하면 ACCOUNT_ALREADY_EXISTS 예외를 던진다")
    void openAccount_duplicateAccount_throwsAlreadyExists() {
        AccountOpenRequest request = mock(AccountOpenRequest.class);
        when(request.getAgreedTermIds()).thenReturn(VALID_TERMS);
        when(otpService.isVerified(USER_ID)).thenReturn(true);
        when(accountRepository.existsByUserUserIdAndAccountType(USER_ID, Account.TYPE_DON_WORRY)).thenReturn(true);

        assertThatThrownBy(() -> accountOpenService.openAccount(USER_ID, request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS);

        verify(accountRepository, never()).save(any());
    }
}
