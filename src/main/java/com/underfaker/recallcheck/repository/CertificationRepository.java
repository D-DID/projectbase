package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Certification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/** certification — KC인증 API 캐시. PK 는 API 의 certUid */
public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findByCertNum(String certNum);

    List<Certification> findByModelNameContaining(String modelName);

    /** 10/3 — 관리자 갱신용: 아직 동기화 시각이 없거나 before 보다 오래된 캐시 (정렬은 pageable 로) */
    List<Certification> findBySyncedAtIsNullOrSyncedAtBefore(LocalDateTime before, Pageable pageable);
}
