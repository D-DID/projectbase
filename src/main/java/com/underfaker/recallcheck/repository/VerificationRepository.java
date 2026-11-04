package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Verification;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import com.underfaker.recallcheck.entity.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** verification — 검증 요청 1건 */
public interface VerificationRepository extends JpaRepository<Verification, Long> {

    Page<Verification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 9/13 추가 — "제품 정보로 리콜 검증"(WEB) / "쿠팡 구매 이력 검증"(EXTENSION) 화면별 이력 분리 조회 */
    Page<Verification> findByUserIdAndChannelOrderByCreatedAtDesc(
            Long userId, VerificationChannel channel, Pageable pageable);

    /**
     * 10/31 — 이력은 '마지막으로 확인한 순서'(updated_at)로 보여 준다. 같은 입력을 다시 확인하면 새로 쌓지 않고
     * 기존 검증의 updated_at 만 갱신하므로(VerificationService#reuseVerification), 만든 순서로 정렬하면
     * 방금 다시 확인한 건이 목록 뒤쪽에 묻힌다.
     */
    Page<Verification> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Verification> findByUserIdAndChannelOrderByUpdatedAtDescIdDesc(
            Long userId, VerificationChannel channel, Pageable pageable);

    /**
     * 10/31 — 검증 이력 중복 정리: 제품명이 같은 이 사용자·채널의 검증(최근 것부터).
     * 나머지 항목(모델명·KC 번호·KC 칸 상태 등)은 호출하는 쪽이 InputFingerprint 로 다시 비교한다.
     */
    @Query("""
            SELECT v FROM Verification v
            WHERE v.userId = :userId AND v.channel = :channel AND v.status = :status
              AND v.id IN (SELECT e.verificationId FROM Extraction e WHERE e.productName = :productName)
            ORDER BY v.id DESC
            """)
    List<Verification> findSameProductName(@Param("userId") Long userId,
                                           @Param("channel") VerificationChannel channel,
                                           @Param("status") VerificationStatus status,
                                           @Param("productName") String productName,
                                           Pageable pageable);

    /** 10/31 — 제품명 없이 KC 인증번호만 넣은 입력용. 위와 같다 */
    @Query("""
            SELECT v FROM Verification v
            WHERE v.userId = :userId AND v.channel = :channel AND v.status = :status
              AND v.id IN (SELECT e.verificationId FROM Extraction e
                           WHERE e.productName IS NULL AND e.certNum = :certNum)
            ORDER BY v.id DESC
            """)
    List<Verification> findSameCertNum(@Param("userId") Long userId,
                                       @Param("channel") VerificationChannel channel,
                                       @Param("status") VerificationStatus status,
                                       @Param("certNum") String certNum,
                                       Pageable pageable);
}
