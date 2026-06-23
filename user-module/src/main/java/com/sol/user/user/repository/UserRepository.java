package com.sol.user.user.repository;

import com.sol.user.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByPasswordIsNotNullOrderByUserIdAsc();
}
