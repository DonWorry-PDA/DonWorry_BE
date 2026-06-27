package com.sol.user.terms.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.terms.dto.TermsConsentRequest;
import com.sol.user.terms.dto.TermsConsentResponse;
import com.sol.user.terms.dto.TermsStatusResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermsService termsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
    }

    // ─── getOptionalTerms ───────────────────────────────────────────────────

    @Test
    void returnsDefaultFalseWhenNoConsentGiven() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TermsStatusResponse response = termsService.getOptionalTerms(1L);

        assertThat(response.thirdParty().agreed()).isFalse();
        assertThat(response.thirdParty().agreedAt()).isNull();
        assertThat(response.marketing().agreed()).isFalse();
        assertThat(response.marketing().agreedAt()).isNull();
    }

    @Test
    void returnsAgreedAtDateWhenConsentGiven() {
        user.updateTermConsent("thirdParty", true, LocalDateTime.of(2026, 6, 12, 10, 0));
        user.updateTermConsent("marketing", true, LocalDateTime.of(2026, 6, 27, 9, 0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TermsStatusResponse response = termsService.getOptionalTerms(1L);

        assertThat(response.thirdParty().agreed()).isTrue();
        assertThat(response.thirdParty().agreedAt()).hasToString("2026-06-12");
        assertThat(response.marketing().agreed()).isTrue();
        assertThat(response.marketing().agreedAt()).hasToString("2026-06-27");
    }

    @Test
    void throwsUserNotFoundWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termsService.getOptionalTerms(99L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ─── updateConsent ──────────────────────────────────────────────────────

    @Test
    void agreesThirdPartyAndSetsAgreedAt() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TermsConsentResponse response = termsService.updateConsent(1L, "thirdParty", new TermsConsentRequest(true));

        assertThat(response.termId()).isEqualTo("thirdParty");
        assertThat(response.agreed()).isTrue();
        assertThat(response.agreedAt()).isNotNull();
        assertThat(user.getThirdPartyAgreed()).isTrue();
        assertThat(user.getThirdPartyAgreedAt()).isNotNull();
    }

    @Test
    void disagreesMarketingAndClearsAgreedAt() {
        user.updateTermConsent("marketing", true, LocalDateTime.now().minusDays(5));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TermsConsentResponse response = termsService.updateConsent(1L, "marketing", new TermsConsentRequest(false));

        assertThat(response.termId()).isEqualTo("marketing");
        assertThat(response.agreed()).isFalse();
        assertThat(response.agreedAt()).isNull();
        assertThat(user.getMarketingAgreed()).isFalse();
        assertThat(user.getMarketingAgreedAt()).isNull();
    }

    @Test
    void throwsInvalidTermIdForUnknownTermId() {
        assertThatThrownBy(() -> termsService.updateConsent(1L, "required", new TermsConsentRequest(true)))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TERM_ID);
    }

    @Test
    void throwsInvalidTermIdForMandatoryTermId() {
        assertThatThrownBy(() -> termsService.updateConsent(1L, "account", new TermsConsentRequest(true)))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TERM_ID);
    }
}
