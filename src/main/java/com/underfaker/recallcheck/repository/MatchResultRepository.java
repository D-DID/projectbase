package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.MatchResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** match_result — 후보별 판정 근거 */
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByVerificationIdOrderBySimilarityScoreDesc(Long verificationId);

    /**
     * 9/27 추가 — 사진 확인(사용자 클릭)으로 다시 채점할 때 이 검증의 이전 후보를 지운다.
     * 파생 삭제 쿼리라 호출하는 쪽에 쓰기 트랜잭션이 있어야 한다(MatchingService 는 클래스 단위 @Transactional).
     */
    void deleteByVerificationId(Long verificationId);
}
