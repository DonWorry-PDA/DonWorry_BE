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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountOpenService {

    private static final List<String> REQUIRED_TERM_IDS = List.of(
            "account", "deposit", "account-privacy", "account-third-party"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OtpService otpService;
    private final ShinhanCertService shinhanCertService;

    public void validateTerms(List<String> agreedTermIds) {
        Set<String> agreed = Set.copyOf(agreedTermIds);
        boolean allAgreed = REQUIRED_TERM_IDS.stream().allMatch(agreed::contains);
        if (!allAgreed) {
            throw new BaseException(ErrorCode.TERMS_NOT_AGREED);
        }
    }

    @Transactional(readOnly = true)
    public IdentityResponse getIdentity(Long userId) {
        User user = findUser(userId);
        return IdentityResponse.builder()
                .name(user.getName())
                .idNumberMasked(maskIdNumber(user.getIdNumber()))
                .phone(user.getPhone())
                .build();
    }

    @Transactional
    public AccountOpenResponse openAccount(Long userId, AccountOpenRequest request) {
        validateTerms(request.getAgreedTermIds());

        if (!isIdentityVerified(userId)) {
            throw new BaseException(ErrorCode.OTP_NOT_VERIFIED);
        }

        if (accountRepository.existsByUserUserIdAndAccountType(userId, Account.TYPE_DON_WORRY)) {
            throw new BaseException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        User user = findUser(userId);
        LocalDate today = LocalDate.now();
        String accountNumber = generateAccountNumber();

        Account account = Account.createDonWorry(user, accountNumber, today);
        try {
            accountRepository.save(account);
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        otpService.clearVerified(userId);
        shinhanCertService.clearVerified(userId);

        return AccountOpenResponse.builder()
                .accountNumber(accountNumber)
                .openedAt(today.format(DATE_FORMATTER))
                .build();
    }

    private boolean isIdentityVerified(Long userId) {
        return otpService.isVerified(userId) || shinhanCertService.isVerified(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    }

    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 6) {
            return null;
        }
        return idNumber.substring(0, 6) + "-●●●●●●●";
    }

    private String generateAccountNumber() {
        for (int i = 0; i < 5; i++) {
            int middle = 100 + secureRandom.nextInt(900);
            int last = 100000 + secureRandom.nextInt(900000);
            String candidate = String.format("110-%03d-%06d", middle, last);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
