package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Verification;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** verification — 검증 요청 1건 */
public interface VerificationRepository extends JpaRepository<Verification, Long> {

    Page<Verification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 9/13 추가 — "제품 정보로 리콜 검증"(WEB) / "쿠팡 구매 이력 검증"(EXTENSION) 화면별 이력 분리 조회 */
    Page<Verification> findByUserIdAndChannelOrderByCreatedAtDesc(
            Long userId, VerificationChannel channel, Pageable pageable);
}
