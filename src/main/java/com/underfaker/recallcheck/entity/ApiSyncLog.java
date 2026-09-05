package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** api_sync_log — API 동기화 실행 이력 (FR-017) */
@Getter
@Entity
@Table(name = "api_sync_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_log_id")
    private Long id;

    /** 동기화 대상 (RECALL / CERTIFICATION) */
    @Column(name = "api_type", nullable = false, length = 30)
    private String apiType;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncStatus status;

    /** 외부 API 결과 코드 */
    @Column(name = "result_code", length = 20)
    private String resultCode;

    /** 처리 건수 */
    @Column(name = "processed_count")
    private Integer processedCount;

    @Column(name = "message", length = 1000)
    private String message;

    @Builder
    public ApiSyncLog(String apiType, SyncStatus status, String resultCode,
                      Integer processedCount, String message) {
        this.apiType = apiType;
        this.executedAt = LocalDateTime.now();
        this.status = status;
        this.resultCode = resultCode;
        this.processedCount = processedCount;
        this.message = message;
    }
}
