package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Verification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** verification — 검증 요청 1건 */
public interface VerificationRepository extends JpaRepository<Verification, Long> {

    Page<Verification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
