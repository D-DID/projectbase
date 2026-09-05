package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** certification — KC인증 API 캐시. PK 는 API 의 certUid */
public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findByCertNum(String certNum);

    List<Certification> findByModelNameContaining(String modelName);
}
