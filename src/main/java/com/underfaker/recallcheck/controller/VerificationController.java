package com.underfaker.recallcheck.controller;

import com.underfaker.recallcheck.common.ApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.request.ImageVerifyRequest;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.dto.request.UrlVerifyRequest;
import com.underfaker.recallcheck.dto.response.MatchEvidenceResponse;
import com.underfaker.recallcheck.dto.response.VerificationHistoryResponse;
import com.underfaker.recallcheck.dto.response.VerificationResultResponse;
import com.underfaker.recallcheck.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-003,004,008,013,014,015 — 검증 전체 */
@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    /** FR-003 URL 입력 검증 */
    @PostMapping("/url")
    public ApiResponse<VerificationResultResponse> verifyByUrl(@Valid @RequestBody UrlVerifyRequest request) {
        return ApiResponse.success(verificationService.verifyByUrl(request));
    }

    /** FR-004 이미지 업로드 검증 */
    @PostMapping(value = "/image", consumes = "multipart/form-data")
    public ApiResponse<VerificationResultResponse> verifyByImage(@Valid @ModelAttribute ImageVerifyRequest request) {
        return ApiResponse.success(verificationService.verifyByImage(request));
    }

    /** FR-008 사용자 직접 입력 검증 — 추출부터 4단계 판정까지 */
    @PostMapping("/manual")
    public ApiResponse<VerificationResultResponse> verifyByManualInput(
            @Valid @RequestBody ManualInputRequest request) {
        return ApiResponse.success(verificationService.verifyByManualInput(request));
    }

    /**
     * FR-008 사용자 직접 입력 검증(배치) — 크롬 확장이 쿠팡 주문내역 페이지에서 한 번에 여러 건을
     * 추출했을 때 사용. 9/13 결정: 필드 구조는 기존 /manual 과 동일한 6필드 그대로, 배열로만 받음
     * (썸네일 이미지 매칭은 아직 이 계약에 안 들어감 — 2단계에서 별도 추가 예정).
     * 항목 하나가 실패해도 나머지 항목은 계속 처리됨(각 항목 독립 트랜잭션) — 상세는
     * VerificationService#verifyByManualInputBatch 주석 참고.
     */
    @PostMapping("/manual/batch")
    public ApiResponse<List<VerificationResultResponse>> verifyByManualInputBatch(
            @Valid @RequestBody List<@Valid ManualInputRequest> requests) {
        return ApiResponse.success(verificationService.verifyByManualInputBatch(requests));
    }

    /** FR-013 검증 결과 조회 */
    @GetMapping("/{verificationId}")
    public ApiResponse<VerificationResultResponse> getResult(@PathVariable Long verificationId) {
        return ApiResponse.success(verificationService.getResult(verificationId));
    }

    /** FR-014 판정 근거(항목별 대조·유사도) 조회 */
    @GetMapping("/{verificationId}/evidence")
    public ApiResponse<MatchEvidenceResponse> getEvidence(@PathVariable Long verificationId) {
        return ApiResponse.success(verificationService.getEvidence(verificationId));
    }

    /** FR-015 내 검증 이력 조회 */
    @GetMapping("/me")
    public ApiResponse<PageResponse<VerificationHistoryResponse>> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(verificationService.getMyHistory(page, size));
    }
}
