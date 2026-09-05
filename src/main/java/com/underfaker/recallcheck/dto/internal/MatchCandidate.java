package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.entity.enums.Decision;

import java.util.List;

/** 리콜 후보 1건 + 점수 */
public record MatchCandidate(

        Long recallUid,
        double similarityScore,
        Decision decision,
        String reason,
        List<FieldComparison> comparisons
) {
}
