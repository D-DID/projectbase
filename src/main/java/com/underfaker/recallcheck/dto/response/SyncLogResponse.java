package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.ApiType;

import java.time.LocalDateTime;

/** FR-017 동기화 실행 이력 */
public record SyncLogResponse(

        Long syncId,
        Long adminId,
        ApiType apiType,
        String resultCode,
        Integer recordCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
