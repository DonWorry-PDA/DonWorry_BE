package com.sol.user.account.repository;

import com.sol.user.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserUserId(Long userId);

    List<Account> findByUserUserIdAndAccountTypeIn(Long userId, List<String> accountTypes);
}
