package com.underfaker.recallcheck.controller;

import com.underfaker.recallcheck.common.ApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
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

    /**
     * FR-016 리콜 데이터 동기화 — 단일 공표일.
     *
     * 적재 대상 날짜는 application.properties 의 openapi.safety-korea.recall-publish-date 에서 읽는다.
     * 날짜를 바꾸려면 프로퍼티를 고치고 재기동해야 하므로, 여러 날짜를 넣을 때는 아래 /recalls/range 를 쓴다.
     */
    @PostMapping("/recalls")
    public ApiResponse<SyncLogResponse> syncRecalls(@RequestParam Long adminId) {
        // TODO adminId 는 인증 구현 후 SecurityContext 에서 꺼내도록 교체할 것
        return ApiResponse.success(recallSyncService.sync(adminId));
    }

    /**
     * FR-016 리콜 데이터 동기화 — 기간.
     *
     * 목록 API 는 공표일 하나씩만 조회할 수 있고 1회 응답이 1,000건으로 끊기므로,
     * 전건 적재는 이 엔드포인트로 날짜를 훑어서 한다.
     *
     * <pre>
     * POST /api/admin/sync/recalls/range?adminId=1&amp;from=20260101&amp;to=20260331&amp;withImages=false
     * </pre>
     *
     * 권장 절차
     *   1. withImages=false 로 분기(3개월)씩 끊어서 본문만 먼저 적재한다.
     *      상세 API 를 안 부르므로 훨씬 빠르고, 요청 하나가 타임아웃될 위험이 낮다.
     *   2. 본문이 다 들어온 뒤 /recalls/images 를 여러 번 호출해 사진을 채운다.
     *
     * @param from           시작 공표일 yyyyMMdd
     * @param to             종료 공표일 yyyyMMdd (포함). 한 번에 400일까지.
     * @param withImages     상세 API 로 사진까지 채울지 (기본 false)
     * @param max            전체 적재 상한. 0 이면 제한 없음 (기본 0)
     * @param categoryFilter category_name 에 이 문자열이 포함된 건만 적재.
     *                       유아·어린이제품만 넣으려면 "어린이" 를 넘긴다. 생략하면 전부.
     */
    @PostMapping("/recalls/range")
    public ApiResponse<RangeSyncResponse> syncRecallRange(
            @RequestParam Long adminId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean withImages,
            @RequestParam(defaultValue = "0") int max,
            @RequestParam(required = false) String categoryFilter) {
        return ApiResponse.success(
                recallSyncService.syncRange(adminId, from, to, withImages, max, categoryFilter));
    }

    /**
     * FR-016 리콜 사진만 뒤늦게 채우기.
     *
     * recall_file 이 하나도 없는 리콜을 골라 상세 API 를 부른다. 건당 1회 호출이라
     * 한 번에 다 돌리면 오래 걸리므로 limit 로 끊어서 여러 번 호출한다.
     * 응답이 0 이 되면 더 채울 게 없다는 뜻이다.
     *
     * <pre>
     * POST /api/admin/sync/recalls/images?limit=200
     * </pre>
     */
    @PostMapping("/recalls/images")
    public ApiResponse<Integer> syncRecallImages(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(recallSyncService.syncImages(limit));
    }

    /** FR-016 KC인증 데이터 동기화 실행 */
    @PostMapping("/certifications")
    public ApiResponse<SyncLogResponse> syncCertifications(@RequestParam Long adminId) {
        return ApiResponse.success(certificationSyncService.sync(adminId));
    }

    /** FR-017 동기화 실행 이력 조회 */
    @GetMapping("/logs")
    public ApiResponse<PageResponse<SyncLogResponse>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(recallSyncService.getLogs(page, size));
    }
}
