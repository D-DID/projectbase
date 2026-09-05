package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.entity.enums.Decision;

import java.util.List;

/**
 * FR-014 판정 근거 — 판정에 사용된 항목과 유사도 점수.
 * 사용자가 판정을 직접 검증할 수 있도록 항목별 대조 결과를 함께 노출한다.
 */
public record MatchEvidenceResponse(

        Long verificationId,
        Long recallUid,
        Decision decision,
        Double similarityScore,
        String reason,
        String recallProductName,
        /** yyyyMMdd */
        String publishDate,
        List<FieldComparison> comparisons
) {
}
