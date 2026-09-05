package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.FinalResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FR-013 검증 결과 — 판정 결과, 리콜 사유, 위해 정보, 소비자 행동요령, 공표일.
 * 판정을 단정하지 않고 4단계로 구분해 반환하며, 공표문 원문 링크를 함께 제공한다.
 */
public record VerificationResultResponse(

        Long verificationId,
        FinalResult finalResult,
        Double similarityScore,

        /* 매칭된 공식 리콜 정보 (NO_MATCH·UNKNOWN 이면 null) */
        Long recallUid,
        String productName,
        String modelName,
        String makerName,
        String recallType,
        String defectContent,
        String consumerAction,
        LocalDate announcedAt,
        String recallUrl,

        LocalDateTime createdAt
) {
}
