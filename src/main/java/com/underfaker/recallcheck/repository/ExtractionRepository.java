package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Extraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** extraction — 소스별 추출 정보 */
public interface ExtractionRepository extends JpaRepository<Extraction, Long> {

    List<Extraction> findByVerificationId(Long verificationId);

    /** 9/13 추가 — 이력 목록(FR-015)에서 페이지 단위로 한 번에 조회할 때 씀 (N+1 방지) */
    List<Extraction> findByVerificationIdIn(List<Long> verificationIds);
}
