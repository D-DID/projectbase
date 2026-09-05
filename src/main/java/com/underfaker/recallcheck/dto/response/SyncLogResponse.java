package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.SyncStatus;

import java.time.LocalDateTime;

/** FR-017 동기화 실행 이력 */
public record SyncLogResponse(

        Long syncLogId,
        String apiType,
        LocalDateTime executedAt,
        SyncStatus status,
        String resultCode,
        Integer processedCount,
        String message
) {
}
