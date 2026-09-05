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

    /** FR-008 추출 실패 시 사용자 직접 입력으로 검증 계속 */
    @PostMapping("/{verificationId}/manual")
    public ApiResponse<VerificationResultResponse> verifyByManualInput(
            @PathVariable Long verificationId,
            @Valid @RequestBody ManualInputRequest request) {
        return ApiResponse.success(verificationService.verifyByManualInput(verificationId, request));
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
