package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.MatchResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** match_result — 후보별 판정 근거 */
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByVerificationIdOrderBySimilarityScoreDesc(Long verificationId);
}
