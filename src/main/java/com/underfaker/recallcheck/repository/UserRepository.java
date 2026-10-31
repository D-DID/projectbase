package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.User;
import com.underfaker.recallcheck.entity.enums.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** user — 사용자·관리자 계정 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 10/31 — 자동 갱신이 동기화 이력(api_sync_log.admin_id)에 남길 관리자(id 가 가장 작은 ADMIN) */
    Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}
