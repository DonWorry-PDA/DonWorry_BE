package com.sol.user.terms.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.terms.dto.TermsConsentRequest;
import com.sol.user.terms.dto.TermsConsentResponse;
import com.sol.user.terms.dto.TermsStatusResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TermsService {

    private static final Set<String> OPTIONAL_TERM_IDS = Set.of("thirdParty", "marketing");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TermsStatusResponse getOptionalTerms(Long userId) {
        User user = findUser(userId);
        return new TermsStatusResponse(
                toTermConsent(user.getThirdPartyAgreed(), user.getThirdPartyAgreedAt()),
                toTermConsent(user.getMarketingAgreed(), user.getMarketingAgreedAt())
        );
    }

    @Transactional
    public TermsConsentResponse updateConsent(Long userId, String termId, TermsConsentRequest request) {
        if (!OPTIONAL_TERM_IDS.contains(termId)) {
            throw new BaseException(ErrorCode.INVALID_TERM_ID);
        }
        User user = findUser(userId);
        LocalDateTime now = LocalDateTime.now();
        user.updateTermConsent(termId, request.agreed(), now);

        LocalDate agreedAt = request.agreed() ? now.toLocalDate() : null;
        return new TermsConsentResponse(termId, request.agreed(), agreedAt);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    }

    private TermsStatusResponse.TermConsent toTermConsent(Boolean agreed, java.time.LocalDateTime agreedAt) {
        boolean isAgreed = Boolean.TRUE.equals(agreed);
        LocalDate date = isAgreed && agreedAt != null ? agreedAt.toLocalDate() : null;
        return new TermsStatusResponse.TermConsent(isAgreed, date);
    }
}
