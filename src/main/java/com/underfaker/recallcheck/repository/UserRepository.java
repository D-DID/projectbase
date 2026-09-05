package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** user — 사용자·관리자 계정 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
