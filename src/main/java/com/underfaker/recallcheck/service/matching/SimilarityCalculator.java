package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 항목별 점수 산정 (FR-010) */
@Component
@RequiredArgsConstructor
public class SimilarityCalculator {

    private final MatchingProperties properties;

    /** 두 문자열의 유사도 (0.0 ~ 1.0). */
    public double similarity(String a, String b) {
        // TODO Levenshtein 또는 Jaro-Winkler
        throw new UnsupportedOperationException("TODO: SimilarityCalculator.similarity");
    }

    /** 항목별 비교 결과에 가중치를 적용한 종합 점수. */
    public double weightedScore(List<FieldComparison> comparisons) {
        // TODO MatchingProperties.Weight 적용
        throw new UnsupportedOperationException("TODO: SimilarityCalculator.weightedScore");
    }
}
