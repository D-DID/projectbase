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
import com.underfaker.recallcheck.entity.Extraction;
import com.underfaker.recallcheck.entity.MatchResult;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.Verification;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.InputType;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.ExtractionRepository;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.repository.VerificationRepository;
import com.underfaker.recallcheck.security.CustomUserDetailsService;
import com.underfaker.recallcheck.service.extraction.ExtractionService;
import com.underfaker.recallcheck.service.matching.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ★ 전체 흐름 총괄 — 추출 → 매칭 → 판정.
 *
 * 의존 방향은 단방향이다.
 *   VerificationService → ExtractionService / MatchingService / RecallRepository
 * 역방향 참조는 금지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final MatchResultRepository matchResultRepository;
    private final RecallRepository recallRepository;
    private final ExtractionRepository extractionRepository;
    private final ExtractionService extractionService;
    private final MatchingService matchingService;

    /**
     * 자기 자신의 프록시 참조. verifyByManualInputBatch() 안에서 verifyByManualInput() 을
     * this.verifyByManualInput() 으로 직접 호출하면 프록시를 안 거쳐서 그 메서드의
     * @Transactional 이 무시된다(스프링 self-invocation 문제). self.verifyByManualInput() 으로
     * 불러야 항목별로 독립된 트랜잭션이 걸린다. 생성자 주입 시 순환참조가 생기므로 @Lazy 필드 주입 사용.
     */
    @Lazy
    @Autowired
    private VerificationService self;

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
        return verifyByManualInput(request, VerificationChannel.WEB);
    }

    /**
     * 9/13 추가 — channel 을 명시하는 버전. "쿠팡 구매 이력 검증"(배치, verifyByManualInputBatch)이
     * 이 메서드를 EXTENSION 으로 호출한다. 위의 1-인자 버전(웹의 "제품 정보로 리콜 검증" 단건 폼이
     * 씀)은 WEB 을 넘기는 얇은 래퍼일 뿐, 로직은 완전히 동일하다.
     */
    @Transactional
    public VerificationResultResponse verifyByManualInput(ManualInputRequest request, VerificationChannel channel) {
        Long userId = requireUserId();

        Verification verification = verificationRepository.save(Verification.builder()
                .userId(userId)
                .inputType(InputType.MANUAL)
                .channel(channel)
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

    /**
     * FR-008 사용자 직접 입력 검증(배치) — 크롬 확장이 쿠팡 주문내역 페이지에서 한 번에 여러 건을
     * 뽑아 보냈을 때 사용. 9/13 결정: 필드는 verifyByManualInput() 과 동일한 6필드, 배열로만 받음.
     *
     * 트랜잭션 격리 방식: 이 메서드 자체는 트랜잭션을 안 열고(NOT_SUPPORTED), 항목마다
     * self.verifyByManualInput() 을 프록시 경유로 호출한다 — 그래야 항목 하나가 각자
     * 자기 트랜잭션 안에서 커밋/롤백된다. 배치 메서드까지 같이 트랜잭션으로 묶으면
     * 한 항목이 던진 예외 때문에 트랜잭션 전체가 rollback-only로 걸려서, try/catch로 잡아도
     * 커밋 시점에 나머지 항목까지 전부 롤백돼버린다(스프링에서 흔히 걸리는 함정).
     *
     * 항목 하나가 실패하면(추출 실패, 매칭 중 예외 등) 그 항목은 로그만 남기고 건너뛰고
     * 나머지는 계속 처리한다 — 몇 건이 실패했는지는 결과 배열 길이와 요청 배열 길이를
     * 비교해서 호출 쪽에서 판단해야 한다(1단계 버전: 실패 건수를 별도 필드로 반환하지 않음).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<VerificationResultResponse> verifyByManualInputBatch(List<ManualInputRequest> requests) {
        List<VerificationResultResponse> results = new ArrayList<>();
        for (ManualInputRequest request : requests) {
            try {
                results.add(self.verifyByManualInput(request, VerificationChannel.EXTENSION));
            } catch (RuntimeException e) {
                log.warn("배치 검증 중 1건 실패, 건너뜀 (productName={})", request.productName(), e);
            }
        }
        return results;
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

    /**
     * FR-015 검증 이력 조회.
     *
     * 9/13 추가 — channel 이 null 이면(기존 동작 그대로) 전체, WEB/EXTENSION 을 넘기면 그 화면
     * 것만. "제품 정보로 리콜 검증" 화면은 channel=WEB, "쿠팡 구매 이력 검증" 화면은
     * channel=EXTENSION 으로 호출하면 됨.
     */
    public PageResponse<VerificationHistoryResponse> getMyHistory(
            int page, int size, VerificationChannel channel) {
        Long userId = requireUserId();
        Page<Verification> found = channel == null
                ? verificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                : verificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(
                        userId, channel, PageRequest.of(page, size));

        // 9/13 추가 — summarize() 버그 수정(아래 참고) 때문에 필요해진 배치 조회. 페이지당 한 번만
        // 쿼리해서 verificationId 로 매핑해두고(N+1 방지), 항목 하나당 extraction 을 따로 안 부른다.
        List<Long> verificationIds = found.getContent().stream().map(Verification::getId).toList();
        Map<Long, Extraction> extractionByVerificationId = extractionRepository
                .findByVerificationIdIn(verificationIds).stream()
                .collect(Collectors.toMap(Extraction::getVerificationId, e -> e, (a, b) -> a));

        return PageResponse.from(found.map(v -> {
            Extraction extraction = extractionByVerificationId.get(v.getId());
            return new VerificationHistoryResponse(
                    v.getId(), v.getInputType(), v.getChannel(),
                    summarize(v, extraction),
                    extraction == null ? null : extraction.getMakerName(),
                    v.getStatus(), v.getFinalResult(), v.getCreatedAt());
        }));
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

    /**
     * 9/13 수정 — 원래는 직접입력 건이면 무조건 "직접 입력" 고정 문자열만 리턴해서 이력 목록에
     * 실제 상품명이 하나도 안 나오는 버그였음(품목 검색/쿠팡 이력 화면 둘 다 재현 확인됨).
     * extraction.product_name 이 있으면 그걸 우선 쓰고, 없을 때만 기존 폴백으로 떨어진다.
     */
    private String summarize(Verification v, Extraction extraction) {
        if (v.getInputUrl() != null) {
            return v.getInputUrl();
        }
        if (v.getImagePath() != null) {
            return v.getImagePath();
        }
        if (extraction != null && extraction.getProductName() != null) {
            return extraction.getProductName();
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
