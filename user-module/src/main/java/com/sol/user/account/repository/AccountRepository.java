package com.sol.user.account.repository;

import com.sol.user.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    void deleteByUserUserId(Long userId);

    List<Account> findByUserUserId(Long userId);
}
