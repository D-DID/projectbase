package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.InputType;
import com.underfaker.recallcheck.entity.enums.VerificationStatus;

import java.time.LocalDateTime;

/** FR-015 검증 이력 목록 항목 */
public record VerificationHistoryResponse(

        Long verificationId,
        InputType inputType,
        String inputSummary,
        VerificationStatus status,
        FinalResult finalResult,
        LocalDateTime createdAt
) {
}
