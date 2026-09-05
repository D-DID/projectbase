package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Certification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** certification — KC인증 API 캐시 */
public interface CertificationRepository extends JpaRepository<Certification, String> {

    List<Certification> findByModelNameContaining(String modelName);
}
