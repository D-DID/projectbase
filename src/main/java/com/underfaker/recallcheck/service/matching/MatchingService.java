package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-010, 011, 012 — 매칭 조율 */
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {

    private final RecallQueryService recallQueryService;
    private final FieldNormalizer fieldNormalizer;
    private final SimilarityCalculator similarityCalculator;
    private final DecisionResolver decisionResolver;
    private final MatchResultRepository matchResultRepository;

    /**
     * FR-010 후보 도출 → FR-011 4단계 판정 → FR-012 항목별 대조 결과 저장.
     *
     * @param verificationId 검증 요청 식별자
     * @param product        통합된 식별 정보
     * @return 판정된 후보 목록 (점수 내림차순)
     */
    public List<MatchCandidate> match(Long verificationId, ExtractedProduct product) {
        // TODO 1) fieldNormalizer 로 입력 정규화
        //      2) recallQueryService 로 캐시에서 후보 조회
        //      3) similarityCalculator 로 항목별 점수 산정
        //      4) decisionResolver 로 임계값 적용해 4단계 판정
        //      5) MatchResult 저장
        throw new UnsupportedOperationException("TODO: MatchingService.match");
    }
}
