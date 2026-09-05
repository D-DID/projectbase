package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.ApiType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * api_sync_log — API 동기화 실행 이력 (FR-017).
 * 실행 시각·결과 코드·처리 건수를 기록한다.
 */
@Getter
@Entity
@Table(name = "api_sync_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_id")
    private Long id;

    /** 동기화를 실행한 관리자 (user.user_id) */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_type", length = 10)
    private ApiType apiType;

    /** 외부 API 결과 코드 (2000 Success / 2004 No Data / 4000 Invalid Auth Key ...) */
    @Column(name = "result_code", length = 10)
    private String resultCode;

    /** 처리 건수 */
    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 동기화 시작 시점에 생성한다. */
    public static ApiSyncLog start(Long adminId, ApiType apiType) {
        ApiSyncLog log = new ApiSyncLog();
        log.adminId = adminId;
        log.apiType = apiType;
        log.startedAt = LocalDateTime.now();
        return log;
    }

    /** 동기화 종료 시 결과를 기록한다. */
    public void finish(String resultCode, Integer recordCount) {
        this.resultCode = resultCode;
        this.recordCount = recordCount;
        this.finishedAt = LocalDateTime.now();
    }
}
