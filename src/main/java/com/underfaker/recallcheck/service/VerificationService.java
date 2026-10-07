package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.client.GoogleVisionClient;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.ExtractionNotes;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.dto.internal.MatchOutcome;
import com.underfaker.recallcheck.dto.internal.ResultView;
import com.underfaker.recallcheck.dto.request.ImageVerifyRequest;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.dto.request.UrlVerifyRequest;
import com.underfaker.recallcheck.dto.response.KcCertResponse;
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
import com.underfaker.recallcheck.service.kc.KcLookupService;
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
import java.util.Comparator;
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
    /** 9/27 — 사진 확인(사용자 클릭)에서만 쓴다. 자동 호출 없음. */
    private final GoogleVisionClient visionClient;
    /** 10/3 — KC 인증번호 → 인증 DB 조회(KC인증 연동). 예외를 던지지 않는다. */
    private final KcLookupService kcLookupService;

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificationResultResponse verifyByManualInput(ManualInputRequest request) {
        return self.verifyByManualInput(request, VerificationChannel.WEB);
    }

    /**
     * 9/13 추가 — channel 을 명시하는 버전. "쿠팡 구매 이력 검증"(배치, verifyByManualInputBatch)이
     * 이 메서드를 EXTENSION 으로 호출한다. 위의 1-인자 버전(웹의 "제품 정보로 리콜 검증" 단건 폼이
     * 씀)은 WEB 을 넘기는 얇은 래퍼일 뿐, 로직은 완전히 동일하다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificationResultResponse verifyByManualInput(ManualInputRequest request, VerificationChannel channel) {
        requireUserId();
        // 10/3 — KC인증 연동. KC 인증번호 조회(캐시 → KC API)는 검증 트랜잭션을 열기 <b>전에</b> 한다.
        // 외부 API 를 기다리는 동안(최대 수 초) DB 커넥션을 잡고 있지 않게 하려는 것(10/3 리뷰).
        // 직접입력 검증의 추출 결과는 이 요청 값 하나뿐이라 request.certNum() 이 곧 판정에 쓰는 인증번호다.
        // 조회 실패·미등록이어도 판정은 그대로 진행한다(KcLookupService 는 예외를 던지지 않는다).
        KcLookup kc = kcLookupService.lookup(request.certNum());
        return self.verifyByManualInputWithKc(request, channel, kc);
    }

    /**
     * 10/3 — 검증 본체(트랜잭션). verifyByManualInput 이 KC 조회를 트랜잭션 밖에서 끝낸 뒤 프록시로 부른다.
     * 외부에서 직접 부르지 말 것.
     *
     * @param kc KC 인증 DB 조회 결과. 인증번호가 없었으면 null
     */
    @Transactional
    public VerificationResultResponse verifyByManualInputWithKc(ManualInputRequest request,
                                                                VerificationChannel channel, KcLookup kc) {
        Long userId = requireUserId();

        Verification verification = verificationRepository.save(Verification.builder()
                .userId(userId)
                .inputType(InputType.MANUAL)
                .channel(channel)
                .build());

        ExtractedProduct product = extractionService.extractFromManualInput(verification.getId(), request);

        // 10/3 — KC인증 연동. 조회 결과를 판정 입력에 싣는다(KcLookup.applyTo). 매칭은 인증 DB 모델명을
        // 리콜 공표문 모델명과 대조해 정확히 같을 때만 확정 근거로 쓴다(다르면 판정을 깎지 않는다).
        // 조회 결과는 raw_text 에 남겨 판정근거 재계산 때도 같은 값을 쓴다.
        if (kc != null) {
            extractionService.saveKcLookup(verification.getId(), kc);
            product = kc.applyTo(product);
        }

        if (product.isEmpty()) {
            verification.complete(FinalResult.UNKNOWN);
            return toResult(verification, null, null);
        }

        // 9/24 — 이미지 판독을 썼다면 그 결과까지 받아서 extraction 에 남긴다.
        // 그래야 FR-014 판정근거를 다시 계산할 때 판정 당시와 같은 값이 나온다.
        MatchOutcome outcome = matchingService.matchWithImage(verification.getId(), product);
        extractionService.saveImageInsight(verification.getId(), outcome.insight());
        List<MatchCandidate> candidates = outcome.candidates();

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
        MatchResult best = pickBest(results);
        Recall recall = recallRepository.findById(best.getRecallUid()).orElse(null);
        return toResultFrom(verification, best, recall);
    }

    /**
     * 9/27 추가 — 사진으로 찾기. 사용자가 "항목누락" 건의 버튼을 눌렀을 때만 Google Vision 을 부른다.
     *
     * 흐름: 썸네일 → Vision Web Detection(추정 이름·웹 문서 제목) → 그 텍스트로 공공데이터 후보를 더 찾고
     *       전 후보를 이미지판독까지 얹어 다시 채점(MatchingService.matchByImage) → 판정 갱신.
     * 이미 사진 확인을 한 건(FOUND/NONE)은 Vision 을 다시 부르지 않고 현재 결과를 돌려준다 — 사용량 보호.
     * 사진으로 찾은 후보는 최대 "의심"이다. 일치는 인증번호·모델명 같은 확정 근거로만 난다.
     */
    @Transactional
    public VerificationResultResponse checkByImage(Long verificationId) {
        Verification verification = findOwned(verificationId);

        var primary = extractionService.primaryRow(verificationId);
        ExtractionNotes.ImageCheck done = primary == null ? null : ExtractionNotes.imageCheck(primary.getRawText());
        if (done == ExtractionNotes.ImageCheck.FOUND || done == ExtractionNotes.ImageCheck.NONE) {
            return getResult(verificationId);
        }

        ExtractedProduct product = extractionService.loadMerged(verificationId);
        if (product == null || product.thumbnailUrl() == null || product.thumbnailUrl().isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_CHECK_NO_IMAGE);
        }
        if (!visionClient.isAvailable()) {
            throw new BusinessException(ErrorCode.IMAGE_CHECK_UNAVAILABLE,
                    "Google Vision 이 설정되지 않았거나 이번 달 사용 상한에 닿았습니다.");
        }

        // 10/7 — URL 방식만 쓰면 쿠팡 사진을 Google 이 못 읽는 경우가 있다(code=3). 서버가 받아서 넘긴다.
        ImageInsight insight = visionClient.annotateRemote(product.thumbnailUrl());
        if (insight.failed()) {
            // 호출 실패 — 기록하지 않는다(다시 누를 수 있게). 원인은 GoogleVisionClient 로그에 남는다.
            throw new BusinessException(ErrorCode.IMAGE_CHECK_UNAVAILABLE,
                    "사진 판독 호출에 실패했습니다. 잠시 후 다시 눌러 주세요.");
        }

        if (!insight.isUsable()) {
            // 웹에서 이 사진을 못 찾았다 — 텍스트 결과를 그대로 두고 "사진으로도 못 찾음"만 남긴다.
            extractionService.markImageCheck(verificationId, ExtractionNotes.ImageCheck.NONE);
            return getResult(verificationId);
        }

        extractionService.saveImageInsight(verificationId, insight);
        extractionService.markImageCheck(verificationId, ExtractionNotes.ImageCheck.FOUND);

        MatchOutcome outcome = matchingService.matchByImage(verificationId, product, insight);
        List<MatchCandidate> candidates = outcome.candidates();
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
     * FR-014 판정에 사용된 항목과 유사도 점수.
     *
     * 9/17 수정 — comparisons 가 항상 빈 배열로 나가던 것을 실제 항목별 대조표로 채운다.
     * 이전엔 여기서 List.of() 를 그대로 내려서, 화면에서는 "종합 87%" 같은 숫자 하나만
     * 보이고 어느 항목이 왜 맞았는지는 reason 문자열을 읽어야만 알 수 있었다(미구현 상태).
     */
    public MatchEvidenceResponse getEvidence(Long verificationId) {
        Verification verification = findOwned(verificationId);
        List<MatchResult> results =
                matchResultRepository.findByVerificationIdOrderBySimilarityScoreDesc(verificationId);

        if (results.isEmpty()) {
            return new MatchEvidenceResponse(verification.getId(), null, null, null,
                    "대조할 리콜 후보가 없습니다.", null, null, List.of());
        }
        MatchResult best = pickBest(results);
        Recall recall = recallRepository.findById(best.getRecallUid()).orElse(null);

        return new MatchEvidenceResponse(
                verification.getId(),
                best.getRecallUid(),
                best.getDecision(),
                best.getSimilarityScore(),
                best.getReason(),
                recall == null ? null : recall.getRecallProductName(),
                recall == null ? null : recall.getPublishDate(),
                buildComparisons(verificationId, recall));
    }

    /**
     * FR-014 항목별 대조표 조립 (9/17 구현).
     *
     * 왜 저장된 걸 읽지 않고 다시 계산하나:
     * match_result 테이블에는 종합 점수(similarity_score), 일치 항목 이름 문자열(matched_field),
     * 사람이 읽는 설명(reason)만 들어간다. 항목별 입력값·공표문값·점수·가중치를 담는 행이 없어서
     * 저장된 값만으로는 대조표를 복원할 수 없다. 그래서 검증 당시의 입력값(extraction)과
     * 공표문(recall)을 다시 꺼내 MatchingService.compare() 로 같은 계산을 한 번 더 돌린다.
     *
     * 한계: 매칭 규칙(가중치·정규화)을 나중에 바꾸면 과거 검증건의 근거도 새 규칙으로 보인다.
     * 1단계에서는 스키마를 늘리지 않는 쪽이 낫다고 보고 이걸 감수한다 — 항목별 행을 따로
     * 저장하는 건 2단계에서 match_result_field 테이블을 추가하며 처리할 것.
     *
     * @return 후보가 없거나 추출 기록이 없으면 빈 목록 (예외를 던지지 않는다)
     */
    private List<FieldComparison> buildComparisons(Long verificationId, Recall recall) {
        if (recall == null) {
            return List.of();
        }
        ExtractedProduct product = extractionService.loadMerged(verificationId);
        if (product == null) {
            return List.of();
        }
        // 9/24 — 판정 때 쓴 이미지 판독 결과를 되살려 같은 규칙으로 재계산한다.
        // 이전엔 compare(product, recall) 로 이미지 없이 계산해서, 판정 때 있던 imageLabel 행이
        // 근거 화면에서 빠지고 종합 점수도 달라졌다.
        ImageInsight insight = extractionService.loadImageInsight(verificationId);
        return matchingService.evidenceComparisons(product, recall, insight);
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
            ResultView view = viewOf(v, extraction);
            // 9/30 — KC 인증정보 상태를 같이 내려 준다(쿠팡 결과 화면의 "KC 인증 대상 N개" 안내용)
            String rawText = extraction == null ? null : extraction.getRawText();
            return new VerificationHistoryResponse(
                    v.getId(), v.getInputType(), v.getChannel(),
                    summarize(v, extraction),
                    extraction == null ? null : extraction.getMakerName(),
                    v.getStatus(), v.getFinalResult(), v.getCreatedAt(),
                    view.resultState(), view.imageCheck(), view.imageCheckAvailable(), view.missingReason(),
                    ExtractionNotes.kcStatusName(rawText), ExtractionNotes.kcText(rawText),
                    kcLookupName(rawText), kcCertState(rawText));
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

    /**
     * 9/27 — 대표 후보는 판정 등급이 높은 것(일치 > 의심 > 불일치), 같은 등급이면 점수순.
     * 사진 확인으로 "의심"이 된 후보는 종합 점수가 낮을 수 있어서 점수만 보면 불일치 후보가 대표가 된다.
     * verification.final_result 와 결과 화면의 대표 리콜이 어긋나지 않게 한다.
     */
    private MatchResult pickBest(List<MatchResult> results) {
        Comparator<MatchResult> byDecision = Comparator.comparingInt(
                m -> m.getDecision() == null ? Integer.MAX_VALUE : m.getDecision().ordinal());
        Comparator<MatchResult> byScoreDesc = Comparator.comparingDouble(
                (MatchResult m) -> m.getSimilarityScore() == null ? 0.0 : m.getSimilarityScore()).reversed();
        return results.stream()
                .min(byDecision.thenComparing(byScoreDesc))
                .orElse(results.get(0));
    }

    /** 9/27 — 화면 4단계 상태(항목누락 포함)와 사진 확인 버튼 여부 */
    private ResultView viewOf(Verification v, Extraction extraction) {
        return ResultView.of(v.getFinalResult(), v.getChannel(),
                extraction == null ? null : extraction.getRawText(),
                extraction == null ? null : extraction.getThumbnailUrl());
    }

    private ResultView viewOf(Verification v) {
        return viewOf(v, extractionService.primaryRow(v.getId()));
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

    /** 10/3 — 이력 한 줄용 KC 인증 DB 조회 결과 */
    private static String kcLookupName(String rawText) {
        KcLookup kc = KcLookup.fromRawText(rawText);
        return kc == null || kc.status() == null ? null : kc.status().name();
    }

    private static String kcCertState(String rawText) {
        KcLookup kc = KcLookup.fromRawText(rawText);
        return kc == null || !kc.isFound() ? null : kc.certState();
    }

    /** 10/3 — 결과 화면용 KC 인증 DB 조회 결과. 조회하지 않았으면(번호 없음·이전 검증) null. */
    private KcCertResponse kcOf(Verification v) {
        Extraction primary = extractionService.primaryRow(v.getId());
        return primary == null ? null : KcCertResponse.of(KcLookup.fromRawText(primary.getRawText()));
    }

    private VerificationResultResponse toResult(Verification v, MatchCandidate best, Recall r) {
        ResultView rv = viewOf(v);
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
                v.getCreatedAt(),
                rv.resultState(), rv.imageCheck(), rv.imageCheckAvailable(), rv.missingReason(),
                kcOf(v), rv.imageProductName());
    }

    private VerificationResultResponse toResultFrom(Verification v, MatchResult m, Recall r) {
        ResultView rv = viewOf(v);
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
                v.getCreatedAt(),
                rv.resultState(), rv.imageCheck(), rv.imageCheckAvailable(), rv.missingReason(),
                kcOf(v), rv.imageProductName());
    }
}
