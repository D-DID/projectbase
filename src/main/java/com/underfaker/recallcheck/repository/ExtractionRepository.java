package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Extraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** extraction — 소스별 추출 정보 */
public interface ExtractionRepository extends JpaRepository<Extraction, Long> {

    List<Extraction> findByVerificationId(Long verificationId);
}
