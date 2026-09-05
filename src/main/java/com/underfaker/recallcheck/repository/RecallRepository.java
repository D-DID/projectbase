package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Recall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** recall — 국내리콜 API 캐시 */
public interface RecallRepository extends JpaRepository<Recall, Long> {

    /** 바코드 완전 일치 — 가장 강한 단서 */
    List<Recall> findByBarcodeNum(String barcodeNum);

    /** 인증번호 부분 일치 — cert_num 이 콤마 구분 목록이라 LIKE 로 찾는다 */
    List<Recall> findByCertNumContaining(String certNum);

    /** 모델명 부분 일치 — recall_model_name 이 콤마 구분 목록이라 LIKE 로 찾는다 */
    List<Recall> findByRecallModelNameContaining(String modelName);

    /** 제품명 부분 일치 */
    List<Recall> findByRecallProductNameContaining(String productName);

    Page<Recall> findAllByOrderByPublishDateDesc(Pageable pageable);
}
