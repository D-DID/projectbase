package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.request.ImageVerifyRequest;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.dto.request.UrlVerifyRequest;
import com.underfaker.recallcheck.dto.response.MatchEvidenceResponse;
import com.underfaker.recallcheck.dto.response.VerificationHistoryResponse;
import com.underfaker.recallcheck.dto.response.VerificationResultResponse;
import com.underfaker.recallcheck.repository.VerificationRepository;
import com.underfaker.recallcheck.service.extraction.ExtractionService;
import com.underfaker.recallcheck.service.matching.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★ 전체 흐름 총괄 — 추출 → 매칭 → 판정.
 *
 * 의존 방향은 단방향이다.
 *   VerificationService → ExtractionService / MatchingService / RecallQueryService
 * 역방향 참조(ExtractionService 가 VerificationService 를 호출)는 금지.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final ExtractionService extractionService;
    private final MatchingService matchingService;

    /** FR-003 URL 입력 검증 */
    @Transactional
    public VerificationResultResponse verifyByUrl(UrlVerifyRequest request) {
        // TODO 1) Verification(PENDING) 저장
        //      2) extractionService.extractFromUrl(...)
        //      3) 추출 실패 시 FinalResult.UNKNOWN 으로 조기 반환 (사용자 직접 입력 유도)
        //      4) matchingService.match(...)
        //      5) Verification 상태·최종 판정 갱신 후 응답 조립
        throw new UnsupportedOperationException("TODO: verifyByUrl");
    }

    /** FR-004 이미지 업로드 검증 */
    @Transactional
    public VerificationResultResponse verifyByImage(ImageVerifyRequest request) {
        // TODO 이미지 저장 → extractionService.extractFromImage(...) → 이하 verifyByUrl 과 동일
        throw new UnsupportedOperationException("TODO: verifyByImage");
    }

    /** FR-008 자동 추출 실패 시 사용자 직접 입력으로 검증 계속 */
    @Transactional
    public VerificationResultResponse verifyByManualInput(Long verificationId, ManualInputRequest request) {
        // TODO 기존 Verification 에 MANUAL 소스 Extraction 을 추가하고 매칭 재실행
        throw new UnsupportedOperationException("TODO: verifyByManualInput");
    }

    /** FR-013 판정 결과·리콜 사유·행동요령 */
    public VerificationResultResponse getResult(Long verificationId) {
        throw new UnsupportedOperationException("TODO: getResult");
    }

    /** FR-014 판정에 사용된 항목과 유사도 점수 */
    public MatchEvidenceResponse getEvidence(Long verificationId) {
        throw new UnsupportedOperationException("TODO: getEvidence");
    }

    /** FR-015 검증 이력 조회 */
    public PageResponse<VerificationHistoryResponse> getMyHistory(int page, int size) {
        throw new UnsupportedOperationException("TODO: getMyHistory");
    }
}
