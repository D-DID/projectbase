package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.Recall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** recall — 국내리콜 API 캐시 */
public interface RecallRepository extends JpaRepository<Recall, Long> {

    /** 바코드 완전 일치 — 가장 강한 단서. 바코드는 공백 표기 차이가 실질적으로 없어 정규화 대상에서 제외 */
    List<Recall> findByBarcodeNum(String barcodeNum);

    /**
     * 인증번호 부분 일치 — cert_num 이 콤마 구분 목록이라 LIKE 로 찾는다.
     * 9/14 이후 후보조회에는 안 씀(findByNormalizedCertNumContaining 로 대체) — 다른 곳에서
     * 원본 텍스트 그대로 찾아야 할 일이 생길까 봐 지우지 않고 남겨둠.
     */
    List<Recall> findByCertNumContaining(String certNum);

    /**
     * 모델명 부분 일치 — recall_model_name 이 콤마 구분 목록이라 LIKE 로 찾는다.
     * 9/14 이후 후보조회에는 안 씀(findByNormalizedModelNameContaining 로 대체) — 위와 같은 이유로 남겨둠.
     */
    List<Recall> findByRecallModelNameContaining(String modelName);

    /**
     * 제품명 부분 일치(원본 텍스트 기준).
     * 9/14 이후 후보조회에는 안 씀(findByNormalizedProductNameContaining 로 대체) — 위와 같은 이유로 남겨둠.
     */
    List<Recall> findByRecallProductNameContaining(String productName);

    /** 인증번호 부분 일치 — 정규화된 컬럼 기준(공백·기호 차이 무시). 9/14 후보조회 정규화 버그 수정 */
    List<Recall> findByNormalizedCertNumContaining(String normalizedCertNum);

    /** 모델명 부분 일치 — 정규화된 컬럼 기준. 9/14 후보조회 정규화 버그 수정 */
    List<Recall> findByNormalizedModelNameContaining(String normalizedModelName);

    /** 제품명 부분 일치 — 정규화된 컬럼 기준. 9/14 후보조회 정규화 버그 수정 */
    List<Recall> findByNormalizedProductNameContaining(String normalizedProductName);

    /** 9/14 이전 search() 가 실제로는 필터링을 안 하고 그냥 전체 목록만 페이징하던 데 쓰던 메서드. 지금은 안 씀(아래 search 로 대체), 남겨둠 */
    Page<Recall> findAllByOrderByPublishDateDesc(Pageable pageable);

    /**
     * FR-009 캐시 리콜 검색 — GET /api/recalls?productName=...&modelName=...&makerName=...&certNum=...
     * 9/14 구현: 이전엔 RecallSearchRequest 필드를 전부 무시하고 전체 목록만 반환했음(버그 아니라 미구현 상태).
     * productName·modelName·certNum 은 정규화 컬럼 기준(공백 표기 차이 무시), makerName 은 정규화 컬럼이
     * 없어서 원본 텍스트 LIKE. 파라미터가 null 이면 그 조건은 통째로 무시(전체 대상).
     */
    @Query("""
            SELECT r FROM Recall r
            WHERE (:productName IS NULL OR r.normalizedProductName LIKE CONCAT('%', :productName, '%'))
              AND (:modelName IS NULL OR r.normalizedModelName LIKE CONCAT('%', :modelName, '%'))
              AND (:makerName IS NULL OR r.makerName LIKE CONCAT('%', :makerName, '%'))
              AND (:certNum IS NULL OR r.normalizedCertNum LIKE CONCAT('%', :certNum, '%'))
            ORDER BY r.publishDate DESC
            """)
    Page<Recall> search(@Param("productName") String productName,
                         @Param("modelName") String modelName,
                         @Param("makerName") String makerName,
                         @Param("certNum") String certNum,
                         Pageable pageable);
}
