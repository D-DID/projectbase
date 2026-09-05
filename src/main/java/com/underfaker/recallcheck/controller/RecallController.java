package com.underfaker.recallcheck.controller;

import com.underfaker.recallcheck.common.ApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.request.RecallSearchRequest;
import com.underfaker.recallcheck.dto.response.RecallDetailResponse;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 내부 캐시 리콜 조회 (외부 API 를 직접 호출하지 않는다) */
@RestController
@RequestMapping("/api/recalls")
@RequiredArgsConstructor
public class RecallController {

    private final RecallQueryService recallQueryService;

    /** 캐시된 리콜 목록 검색 */
    @GetMapping
    public ApiResponse<PageResponse<RecallDetailResponse>> search(@ModelAttribute RecallSearchRequest request) {
        return ApiResponse.success(recallQueryService.search(request));
    }

    /** 공표문 상세 */
    @GetMapping("/{recallUid}")
    public ApiResponse<RecallDetailResponse> getDetail(@PathVariable Long recallUid) {
        return ApiResponse.success(recallQueryService.getDetail(recallUid));
    }
}
