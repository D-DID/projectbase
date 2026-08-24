package com.meta.projectbase.auth.repository;

import com.meta.projectbase.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // 사용자계정(Username == AccountID)으로 User객체를 조회하는 메서드
    // 스프링시큐리티의 UserDetailsService가 사용함
    Optional<User> findByUsername(String username);

    // 사용자계정이 존재하는지 확인하는 메서드(중복체크 등..)
    boolean existsByUsername(String username);

    // 이메일이 존재하는지 확인하는 메서드(중복체크 등..)
    boolean existsByEmail(String email);
}