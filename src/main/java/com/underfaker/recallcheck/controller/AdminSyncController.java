package com.underfaker.recallcheck.controller;

import com.underfaker.recallcheck.common.ApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.service.sync.CertificationSyncService;
import com.underfaker.recallcheck.service.sync.RecallSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** FR-016 동기화 실행, FR-017 동기화 이력 조회 (관리자 전용) */
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
public class AdminSyncController {

    private final RecallSyncService recallSyncService;
    private final CertificationSyncService certificationSyncService;

    /** FR-016 리콜 데이터 동기화 실행 */
    @PostMapping("/recalls")
    public ApiResponse<SyncLogResponse> syncRecalls() {
        return ApiResponse.success(recallSyncService.sync());
    }

    /** FR-016 KC인증 데이터 동기화 실행 */
    @PostMapping("/certifications")
    public ApiResponse<SyncLogResponse> syncCertifications() {
        return ApiResponse.success(certificationSyncService.sync());
    }

    /** FR-017 동기화 실행 이력 조회 */
    @GetMapping("/logs")
    public ApiResponse<PageResponse<SyncLogResponse>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(recallSyncService.getLogs(page, size));
    }
}
