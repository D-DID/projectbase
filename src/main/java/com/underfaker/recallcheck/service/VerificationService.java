package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.dto.request.ImageVerifyRequest;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.dto.request.UrlVerifyRequest;
import com.underfaker.recallcheck.dto.response.MatchEvidenceResponse;
import com.underfaker.recallcheck.dto.response.VerificationHistoryResponse;
import com.underfaker.recallcheck.dto.response.VerificationResultResponse;
import com.underfaker.recallcheck.entity.MatchResult;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.Verification;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.InputType;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.repository.VerificationRepository;
import com.underfaker.recallcheck.security.CustomUserDetailsService;
import com.underfaker.recallcheck.service.extraction.ExtractionService;
import com.underfaker.recallcheck.service.matching.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ★ 전체 흐름 총괄 — 추출 → 매칭 → 판정.
 *
 * 의존 방향은 단방향이다.
 *   VerificationService → ExtractionService / MatchingService / RecallRepository
 * 역방향 참조는 금지.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final MatchResultRepository matchResultRepository;
    private final RecallRepository recallRepository;
    private final ExtractionService extractionService;
    private final MatchingService matchingService;

    /** FR-003 URL 입력 검증 (미구현) */
    @Transactional
    public VerificationResultResponse verifyByUrl(UrlVerifyRequest request) {
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED,
                "URL 자동 추출(FR-005)은 아직 구현 중입니다. 직접 입력 검증을 사용해 주세요.");
    }

    /** FR-004 이미지 업로드 검증 (미구현) */
    @Transactional
    public VerificationResultResponse verifyByImage(ImageVerifyRequest request) {
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED,
                "이미지 OCR(FR-006)은 아직 구현 중입니다. 직접 입력 검증을 사용해 주세요.");
    }

    /**
     * FR-008 사용자 직접 입력 검증 — 추출부터 4단계 판정까지 end-to-end.
     *
     * 흐름:
     *   1) Verification 생성 (PENDING)
     *   2) 식별 정보 저장·통합 (FR-007)
     *   3) 정보가 아예 없으면 UNKNOWN 으로 조기 종료 — 억지 판정하지 않는다
     *   4) 후보 매칭 및 4단계 판정 (FR-010, 011, 012)
     *   5) 최종 판정 저장 후 결과 반환 (FR-013)
     */
    @Transactional
    public VerificationResultResponse verifyByManualInput(ManualInputRequest request) {
        Long userId = requireUserId();

        Verification verification = verificationRepository.save(Verification.builder()
                .userId(userId)
                .inputType(InputType.MANUAL)
                .build());

        ExtractedProduct product = extractionService.extractFromManualInput(verification.getId(), request);

        if (product.isEmpty()) {
            verification.complete(FinalResult.UNKNOWN);
            return toResult(verification, null, null);
        }

        List<MatchCandidate> candidates = matchingService.match(verification.getId(), product);

        if (candidates.isEmpty()) {
            verification.complete(FinalResult.NO_MATCH);
            return toResult(verification, null, null);
        }

        MatchCandidate best = candidates.get(0);
        verification.complete(toFinalResult(best.decision()));

        Recall recall = best.decision() == Decision.NO_MATCH
                ? null
                : recallRepository.findById(best.recallUid()).orElse(null);

        return toResult(verification, best, recall);
    }

    /** FR-013 판정 결과·리콜 사유·행동요령 */
    public VerificationResultResponse getResult(Long verificationId) {
        Verification verification = findOwned(verificationId);
        List<MatchResult> results =
                matchResultRepository.findByVerificationIdOrderBySimilarityScoreDesc(verificationId);

        if (results.isEmpty()) {
            return toResult(verification, null, null);
        }
        MatchResult best = results.get(0);
        Recall recall = recallRepository.findById(best.getRecallUid()).orElse(null);
        return toResultFrom(verification, best, recall);
    }

    /** FR-014 판정에 사용된 항목과 유사도 점수 */
    public MatchEvidenceResponse getEvidence(Long verificationId) {
        Verification verification = findOwned(verificationId);
        List<MatchResult> results =
                matchResultRepository.findByVerificationIdOrderBySimilarityScoreDesc(verificationId);

        if (results.isEmpty()) {
            return new MatchEvidenceResponse(verification.getId(), null, null, null,
                    "대조할 리콜 후보가 없습니다.", null, null, List.of());
        }
        MatchResult best = results.get(0);
        Recall recall = recallRepository.findById(best.getRecallUid()).orElse(null);

        return new MatchEvidenceResponse(
                verification.getId(),
                best.getRecallUid(),
                best.getDecision(),
                best.getSimilarityScore(),
                best.getReason(),
                recall == null ? null : recall.getRecallProductName(),
                recall == null ? null : recall.getPublishDate(),
                List.of());
    }

    /** FR-015 검증 이력 조회 */
    public PageResponse<VerificationHistoryResponse> getMyHistory(int page, int size) {
        Long userId = requireUserId();
        Page<Verification> found = verificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        return PageResponse.from(found.map(v -> new VerificationHistoryResponse(
                v.getId(), v.getInputType(), summarize(v), v.getStatus(),
                v.getFinalResult(), v.getCreatedAt())));
    }

    // ------------------------------------------------------------------ 내부

    private Verification findOwned(Long verificationId) {
        Long userId = requireUserId();
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));
        if (!verification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        return verification;
    }

    private Long requireUserId() {
        Long userId = CustomUserDetailsService.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return userId;
    }

    private FinalResult toFinalResult(Decision decision) {
        return switch (decision) {
            case MATCH -> FinalResult.MATCH;
            case PARTIAL -> FinalResult.PARTIAL;
            case NO_MATCH -> FinalResult.NO_MATCH;
        };
    }

    private String summarize(Verification v) {
        if (v.getInputUrl() != null) {
            return v.getInputUrl();
        }
        if (v.getImagePath() != null) {
            return v.getImagePath();
        }
        return "직접 입력";
    }

    private VerificationResultResponse toResult(Verification v, MatchCandidate best, Recall r) {
        return new VerificationResultResponse(
                v.getId(), v.getFinalResult(),
                best == null ? null : best.similarityScore(),
                r == null ? null : r.getRecallUid(),
                r == null ? null : r.getRecallProductName(),
                r == null ? null : r.getRecallBrandName(),
                r == null ? null : r.getRecallModelName(),
                r == null ? null : r.getRecallTypeName(),
                r == null ? null : r.getRecallMeans(),
                r == null ? null : r.getRecallCmpnyName(),
                r == null ? null : r.getMakerName(),
                r == null ? null : r.getHarmDscr(),
                r == null ? null : r.getAccidentCaseDscr(),
                r == null ? null : r.getPublishActionDscr(),
                r == null ? null : r.getPublishDate(),
                v.getCreatedAt());
    }

    private VerificationResultResponse toResultFrom(Verification v, MatchResult m, Recall r) {
        return new VerificationResultResponse(
                v.getId(), v.getFinalResult(), m.getSimilarityScore(),
                r == null ? null : r.getRecallUid(),
                r == null ? null : r.getRecallProductName(),
                r == null ? null : r.getRecallBrandName(),
                r == null ? null : r.getRecallModelName(),
                r == null ? null : r.getRecallTypeName(),
                r == null ? null : r.getRecallMeans(),
                r == null ? null : r.getRecallCmpnyName(),
                r == null ? null : r.getMakerName(),
                r == null ? null : r.getHarmDscr(),
                r == null ? null : r.getAccidentCaseDscr(),
                r == null ? null : r.getPublishActionDscr(),
                r == null ? null : r.getPublishDate(),
                v.getCreatedAt());
    }
}
