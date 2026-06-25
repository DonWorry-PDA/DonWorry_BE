package com.sol.user.account.repository;

import com.sol.user.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    void deleteByUserUserId(Long userId);

    List<Account> findByUserUserId(Long userId);

    List<Account> findByUserUserIdOrderByAccountIdAsc(Long userId);

    List<Account> findByUserUserIdAndAccountTypeIn(Long userId, List<String> accountTypes);

    List<Account> findByUserUserIdAndAccountTypeNot(Long userId, String accountType);

    boolean existsByUserUserIdAndAccountType(Long userId, String accountType);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByUserUserIdAndAccountType(Long userId, String accountType);
}
