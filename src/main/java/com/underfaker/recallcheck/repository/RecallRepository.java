package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Recall;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** recall — 국내리콜 API 캐시 */
public interface RecallRepository extends JpaRepository<Recall, Long> {

    List<Recall> findByModelNameContaining(String modelName);

    List<Recall> findByCertNum(String certNum);
}
