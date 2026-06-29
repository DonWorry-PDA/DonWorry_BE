package com.sol.user.account.repository;

import com.sol.user.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT a.accountId FROM Account a WHERE a.user.userId = :userId AND a.accountType = :accountType")
    Optional<Long> findAccountIdByUserIdAndAccountType(@Param("userId") Long userId,
                                                       @Param("accountType") String accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.user.userId = :userId AND a.accountType = :accountType")
    Optional<Account> findByUserUserIdAndAccountTypeForUpdate(@Param("userId") Long userId,
                                                              @Param("accountType") String accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId IN :ids AND a.user.userId = :userId ORDER BY a.accountId ASC")
    List<Account> findAllByIdAndUserIdForUpdate(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    boolean existsByUserUserIdAndInstitutionName(Long userId, String institutionName);
}
