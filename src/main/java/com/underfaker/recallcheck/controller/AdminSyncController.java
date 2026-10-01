package com.underfaker.recallcheck.controller;

import com.underfaker.recallcheck.common.ApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.KeywordSyncResponse;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.service.sync.CertificationSyncService;
import com.underfaker.recallcheck.service.sync.RecallSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 10/1 정정: 공표일 조회는 2023-07-12 이후만 돌려준다. 그 이전 기간을 넣으면 에러 없이
     * fetched=0 이 나온다. 과거분은 아래 /recalls/keywords 로 채운다.
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
     * FR-016 리콜 과거분 적재 — 품목명 키워드 (10/1 추가).
     *
     * 공표일 조회(/recalls/range)는 2023-07-12 이후만 돌려준다. 그 이전(2012~) 공표분은
     * 품목명 부분 일치로만 나오므로 이 엔드포인트로 채운다. 파라미터 없이 부르면
     * 어린이제품 34품목 + 전기·생활용품 키워드 + DB 에 이미 있는 품목명으로 시작해서,
     * 받은 품목명으로 키워드를 넓혀 가며 끝까지 훑는다. 다시 돌려도 결과는 같다(upsert).
     *
     * <pre>
     * POST /api/admin/sync/recalls/keywords?adminId=1
     * POST /api/admin/sync/recalls/keywords?adminId=1&amp;keywords=완구,유모차&amp;expand=false
     * </pre>
     *
     * 응답에서 볼 것: inserted(새로 들어간 건수), insertedByYear, oldestPublishDate,
     * failedKeywords·pendingKeywords(둘 다 비어야 다 돈 것).
     *
     * @param keywords   쉼표 구분 품목명 조각. 생략하면 기본 목록.
     * @param expand     받은 품목명으로 키워드를 넓혀 갈지 (기본 true)
     * @param fromDb     DB 에 있는 품목명도 출발 키워드로 쓸지 (기본 true)
     * @param prune      이미 다 받은 키워드를 포함하는 키워드를 생략할지 (기본 true, 건드릴 일 없음)
     * @param maxCalls   목록 API 최대 호출 수 (기본 600). 모자라면 pendingKeywords 에 남는다.
     * @param withImages 사진까지 채울지 (기본 false — 사진은 /recalls/images 로 따로)
     */
    @PostMapping("/recalls/keywords")
    public ApiResponse<KeywordSyncResponse> syncRecallKeywords(
            @RequestParam Long adminId,
            @RequestParam(required = false) List<String> keywords,
            @RequestParam(defaultValue = "true") boolean expand,
            @RequestParam(defaultValue = "true") boolean fromDb,
            @RequestParam(defaultValue = "true") boolean prune,
            @RequestParam(defaultValue = "600") int maxCalls,
            @RequestParam(defaultValue = "false") boolean withImages) {
        return ApiResponse.success(recallSyncService.syncByKeywords(
                adminId, keywords, expand, fromDb, prune, maxCalls, withImages));
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
