package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.client.GoogleVisionClient;
import com.underfaker.recallcheck.common.ChildProductSignal;
import com.underfaker.recallcheck.common.KcCertNumbers;
import com.underfaker.recallcheck.common.OverseasListing;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.ExtractionNotes;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import com.underfaker.recallcheck.dto.internal.InputFingerprint;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.dto.internal.MatchOutcome;
import com.underfaker.recallcheck.dto.internal.ResultView;
import com.underfaker.recallcheck.dto.internal.UrlExtraction;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.dto.request.UrlVerifyRequest;
import com.underfaker.recallcheck.dto.response.KcCertResponse;
import com.underfaker.recallcheck.dto.response.MatchEvidenceResponse;
import com.underfaker.recallcheck.dto.response.UrlPreviewResponse;
import com.underfaker.recallcheck.dto.response.ProductInputResponse;
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
import com.underfaker.recallcheck.entity.enums.VerificationStatus;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.ExtractionRepository;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.repository.VerificationRepository;
import com.underfaker.recallcheck.security.CustomUserDetailsService;
import com.underfaker.recallcheck.service.extraction.ExtractionService;
import com.underfaker.recallcheck.service.extraction.ImageStore;
import com.underfaker.recallcheck.service.extraction.WebTextExtractor;
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

import java.time.LocalDateTime;
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
    /** 10/31 — FR-005 URL 자동 추출(상품 페이지 받기 + 파싱). 예외를 던지지 않는다. */
    private final WebTextExtractor webTextExtractor;
    /** 10/31 — FR-004 사진 업로드 검증의 원본 저장소 */
    private final ImageStore imageStore;

    /**
     * 자기 자신의 프록시 참조. verifyByManualInputBatch() 안에서 verifyByManualInput() 을
     * this.verifyByManualInput() 으로 직접 호출하면 프록시를 안 거쳐서 그 메서드의
     * @Transactional 이 무시된다(스프링 self-invocation 문제). self.verifyByManualInput() 으로
     * 불러야 항목별로 독립된 트랜잭션이 걸린다. 생성자 주입 시 순환참조가 생기므로 @Lazy 필드 주입 사용.
     */
    @Lazy
    @Autowired
    private VerificationService self;

    /**
     * FR-003·005 URL 입력 검증(10/31 구현) — 서버가 상품 페이지를 받아 식별 정보를 뽑고(WebTextExtractor),
     * 직접입력과 같은 흐름(KC 조회 → 대조 → 판정)을 탄다. 검증은 input_type=URL, channel=WEB 으로 남는다.
     *
     * 페이지 받기(최대 12초)와 KC 조회는 트랜잭션 밖에서 한다(DB 커넥션을 잡고 기다리지 않게 — 10/3·10/10 과 같은 이유).
     * 쿠팡처럼 서버 접속을 막는 쇼핑몰은 V003 + 확장 안내, 식별 정보를 못 찾으면 V004(직접 입력 안내).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificationResultResponse verifyByUrl(UrlVerifyRequest request) {
        requireUserId();
        String url = request.url().trim();
        ManualInputRequest req = withAllCertNumbers(readUrlOrThrow(url).toRequest());
        KcLookup kc = excludedOverseas(req) != null ? null : kcLookupService.lookup(req.certNum());
        return self.verifyUrlWithKc(url, req, kc);
    }

    /**
     * 10/31 — URL 미리 읽기. 검증을 저장하지 않고 뽑은 값만 돌려준다(웹 ② 방식이 칸 채우기에 쓴다).
     * 실패 규칙은 verifyByUrl 과 같다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UrlPreviewResponse previewUrl(UrlVerifyRequest request) {
        requireUserId();
        String url = request.url().trim();
        return UrlPreviewResponse.of(url, readUrlOrThrow(url));
    }

    private UrlExtraction readUrlOrThrow(String url) {
        UrlExtraction ex = webTextExtractor.read(url);
        return switch (ex.status()) {
            case OK -> ex;
            case BLOCKED_SITE -> throw new BusinessException(ErrorCode.PAGE_FETCH_FAILED,
                    "이 쇼핑몰(" + ex.site() + ")은 서버 접속을 막아 서버에서 읽을 수 없습니다. "
                            + "쿠팡 상품은 크롬 확장으로 불러오세요(② 쿠팡 URL 붙여넣기 · ③ 영역 드래그).");
            case NOT_ALLOWED -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "열 수 없는 주소입니다(내부망 주소이거나 주소 형식이 잘못됐습니다).");
            case FETCH_FAILED -> throw new BusinessException(ErrorCode.PAGE_FETCH_FAILED,
                    "상품 페이지를 불러오지 못했습니다(쇼핑몰 차단·시간 초과·HTML 아님). "
                            + "③ 영역 드래그나 ① 직접 입력을 쓰세요.");
            case EMPTY -> throw new BusinessException(ErrorCode.EXTRACTION_EMPTY,
                    "페이지는 열었지만 제품명·모델명·KC 인증번호를 찾지 못했습니다. ③ 영역 드래그나 ① 직접 입력을 쓰세요.");
        };
    }

    /**
     * FR-004 사진 업로드 검증(10/31 구현) — 사용자가 올린 제품 사진으로 '사진으로 찾기'와 같은 2차 흐름을 탄다.
     *
     * OCR(FR-006)은 10/1 스코프 컷이라 사진 속 글자는 읽지 않는다. 대신 Google Vision 웹 검색으로 같은 사진이 실린
     * 웹 문서를 찾고, 서로 다른 사이트 2곳 이상에서 반복된 상품명·KC 번호·모델명으로 공표문을 대조한다(10/10 최종 흐름 2차).
     * 판매자가 적은 값이 아니라 결과는 최대 '의심'이다. 반복된 값을 못 찾으면 '확인불가'.
     *
     * 사진 저장(디스크)·Vision 호출(수 초)은 트랜잭션 밖에서 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificationResultResponse verifyByImage(byte[] imageBytes) {
        requireUserId();
        if (!visionClient.isAvailable()) {
            throw new BusinessException(ErrorCode.IMAGE_CHECK_UNAVAILABLE,
                    "Google Vision 이 설정되지 않았거나 이번 달 사용 상한에 닿아 사진으로 확인할 수 없습니다.");
        }
        String imagePath = imageStore.save(imageBytes);   // 형식·크기 검사(V002) 포함
        ImageInsight insight = visionClient.annotateBytes(imageBytes);
        if (insight.failed()) {
            throw new BusinessException(ErrorCode.IMAGE_CHECK_UNAVAILABLE,
                    "사진 판독 호출에 실패했습니다. 잠시 후 다시 올려 주세요.");
        }
        return self.verifyImageWithInsight(imagePath, insight);
    }

    /**
     * 10/31 — 사진 업로드 검증 본체(트랜잭션). verifyByImage 가 Vision 호출을 끝낸 뒤 프록시로 부른다.
     * 외부에서 직접 부르지 말 것.
     */
    @Transactional
    public VerificationResultResponse verifyImageWithInsight(String imagePath, ImageInsight insight) {
        Long userId = requireUserId();
        Verification verification = verificationRepository.save(Verification.builder()
                .userId(userId)
                .inputType(InputType.IMAGE)
                .imagePath(imagePath)
                .channel(VerificationChannel.WEB)
                .build());
        Long id = verification.getId();
        ExtractedProduct product = extractionService.extractFromImage(id, insight.productName());
        if (!insight.isUsable()) {
            extractionService.markImageCheck(id, ExtractionNotes.ImageCheck.NONE);
            verification.complete(FinalResult.UNKNOWN);
            return toResult(verification, null, null);
        }
        extractionService.saveImageInsight(id, insight);
        extractionService.markImageCheck(id, ExtractionNotes.ImageCheck.FOUND);
        MatchOutcome outcome = matchingService.matchByImage(id, product, insight);
        List<MatchCandidate> candidates = outcome.candidates();
        recordEvidence(id, candidates);
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
     * 10/31 — URL 검증 본체(트랜잭션). verifyByUrl 이 페이지 읽기·KC 조회를 끝낸 뒤 프록시로 부른다.
     * 외부에서 직접 부르지 말 것.
     */
    @Transactional
    public VerificationResultResponse verifyUrlWithKc(String url, ManualInputRequest request, KcLookup kc) {
        Long userId = requireUserId();
        Verification verification = verificationRepository.save(Verification.builder()
                .userId(userId)
                .inputType(InputType.URL)
                .inputUrl(url)
                .channel(VerificationChannel.WEB)
                .build());
        ExtractedProduct product = extractionService.extractFromUrl(verification.getId(), request);
        return judge(verification, product, request, kc);
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
        Long userId = requireUserId();
        // 10/10 — 최종 흐름 1차는 KC 인증번호 기준이다. 확장 0.4.4 는 KC 칸의 첫 번호만 certNum 으로 보내므로
        // KC 칸 원문(kcText)에 남은 번호까지 모아 판정에 쓴다(KcCertNumbers.joinAll).
        ManualInputRequest req = withAllCertNumbers(request);
        // 10/31 — 검증 이력 중복 정리. 같은 사용자·채널에 같은 입력으로 끝난 검증이 있으면 새로 쌓지 않는다.
        // KC 조회(외부 API)도 다시 하지 않는다. 그 사이 리콜 DB 가 갱신됐으면 그 검증을 다시 판정한다.
        Long sameId = self.findSameInput(userId, channel, req);
        if (sameId != null) {
            return self.reuseVerification(sameId);
        }
        // 10/10 — 해외직구·구매대행 상품은 판단하지 않는다(팀장 결정). KC 조회도 하지 않는다.
        // 단 어린이제품은 판단한다 — 구매대행도 KC 표시가 없으면 금지(어린이제품법 제30조). excludedOverseas 참조.
        boolean overseas = excludedOverseas(req) != null;
        // 10/3 — KC인증 연동. KC 인증번호 조회(캐시 → KC API)는 검증 트랜잭션을 열기 <b>전에</b> 한다.
        // 외부 API 를 기다리는 동안(최대 수 초) DB 커넥션을 잡고 있지 않게 하려는 것(10/3 리뷰).
        // 직접입력 검증의 추출 결과는 이 요청 값 하나뿐이라 req.certNum() 이 곧 판정에 쓰는 인증번호다.
        // 조회 실패·미등록이어도 판정은 그대로 진행한다(KcLookupService 는 예외를 던지지 않는다).
        KcLookup kc = overseas ? null : kcLookupService.lookup(req.certNum());
        return self.verifyByManualInputWithKc(req, channel, kc);
    }

    /**
     * 10/10 — 판단에서 뺄 국외 판매 상품이면 그 표시(예: "해외직구"), 아니면 null.
     *
     * 해외직구·구매대행 표시가 있어도 <b>어린이제품이면 빼지 않는다</b>(팀장 확인 10/10).
     *   어린이제품 안전 특별법 제30조 — 판매중개업자·구매·수입대행업자는 안전인증·안전확인·공급자적합성확인 표시가 없는
     *   안전관리대상어린이제품의 판매를 중개하거나 구매·수입을 대행해서는 안 된다(시행 2025.10.1, 국가법령정보센터 원문).
     *   제4차 어린이제품 안전관리 기본계획(2025.1) 2-③ "KC 미인증 구매대행 어린이제품 등 불법제품 안전관리 강화".
     * 즉 어린이제품은 구매대행이어도 KC 가 필수라 판단 대상이다. 어린이제품이 아닌 해외직구·구매대행 상품만 뺀다.
     */
    static String excludedOverseas(ManualInputRequest r) {
        String signal = OverseasListing.signal(r.productName(), r.kcText());
        if (signal == null || ChildProductSignal.likely(r.productName(), r.kcText())) {
            return null;
        }
        return signal;
    }

    /** extraction.cert_num 칸 길이 */
    private static final int CERT_NUM_MAX_LENGTH = 64;

    /**
     * 10/10 — KC 칸 원문(kcText)에 인증번호 칸에 없던 번호가 더 있으면, 번호를 모두 모아 certNum 으로 바꾼 요청.
     * 새로 나온 번호가 없으면 원래 요청 그대로다 — 판매자·사용자가 적은 원문을 cert_num 에 그대로 남긴다(KcLookup.applyTo 주석).
     * 바꿀 때도 KC 칸 원문은 [detail] 블록(kcText)에 남는다. 칸 길이(64자)를 넘으면 뒤 번호부터 뺀다.
     */
    static ManualInputRequest withAllCertNumbers(ManualInputRequest r) {
        String all = KcCertNumbers.joinAll(r.certNum(), r.kcText());
        List<String> numbers = KcCertNumbers.extract(all);
        if (all == null || numbers.size() <= KcCertNumbers.extract(r.certNum()).size()) {
            return r;
        }
        while (all.length() > CERT_NUM_MAX_LENGTH && numbers.size() > 1) {
            numbers = numbers.subList(0, numbers.size() - 1);
            all = String.join(", ", numbers);
        }
        return new ManualInputRequest(r.productName(), r.brandName(), r.modelName(), r.makerName(),
                r.barcodeNum(), all, r.thumbnailUrl(), r.kcStatus(), r.kcText());
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
        return judge(verification, product, request, kc);
    }

    /**
     * 10/31 — 저장된 입력으로 판정해 검증을 끝낸다(직접입력·확장·URL 공통). 트랜잭션 안에서 부른다.
     * (10/30 까지 verifyByManualInputWithKc 본문이던 것을 URL 검증과 같이 쓰려고 뺐다 — 동작은 같다)
     */
    private VerificationResultResponse judge(Verification verification, ExtractedProduct product,
                                             ManualInputRequest request, KcLookup kc) {
        // 10/10 — 해외직구·구매대행 상품은 판단하지 않는다(팀장 결정: 국외 제품은 판단 대상이 아니다).
        // 입력은 이력에 남기고, 판정은 UNKNOWN 으로 저장한다(final_result ENUM). 화면에는 '판단 제외'(ResultView.EXCLUDED).
        // 어린이제품은 빼지 않고 판단한다 — 메모만 남겨 화면에 제30조 안내를 붙인다.
        String overseas = OverseasListing.signal(request.productName(), request.kcText());
        if (overseas != null) {
            if (excludedOverseas(request) != null) {
                extractionService.markOverseas(verification.getId(), overseas);
                verification.complete(FinalResult.UNKNOWN);
                return toResult(verification, null, null);
            }
            extractionService.markOverseasChild(verification.getId(), overseas);
        }

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
        recordEvidence(verification.getId(), candidates);

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

    /** 같은 입력 후보를 몇 건까지 비교할지 — 제품명이 같은 검증이 이보다 많으면 최근 것만 본다 */
    private static final int SAME_INPUT_SCAN = 20;

    /**
     * 10/31 — 검증 이력 중복 정리: 이 사용자·채널에서 같은 입력(InputFingerprint)으로 끝난 가장 최근 검증 id.
     * 없으면 null. 제품명(없으면 KC 인증번호)으로 후보를 좁힌 뒤 나머지 항목을 비교한다.
     * verifyByManualInput 이 프록시로 부른다.
     */
    public Long findSameInput(Long userId, VerificationChannel channel, ManualInputRequest request) {
        InputFingerprint key = InputFingerprint.of(request);
        if (key == null || key.isBlank()) {
            return null;
        }
        List<Verification> found;
        PageRequest page = PageRequest.of(0, SAME_INPUT_SCAN);
        if (request.productName() != null && !request.productName().isBlank()) {
            found = verificationRepository.findSameProductName(userId, channel, VerificationStatus.DONE,
                    request.productName(), page);
        } else if (request.certNum() != null && !request.certNum().isBlank()) {
            found = verificationRepository.findSameCertNum(userId, channel, VerificationStatus.DONE,
                    request.certNum(), page);
        } else {
            return null;
        }
        for (Verification v : found) {
            if (v.getInputUrl() != null || v.getImagePath() != null) {
                continue; // URL·사진 업로드 검증은 입력 방식이 달라 묶지 않는다
            }
            if (InputFingerprint.same(key, InputFingerprint.of(extractionService.primaryRow(v.getId())))) {
                return v.getId();
            }
        }
        return null;
    }

    /**
     * 10/31 — 같은 입력의 기존 검증을 다시 쓴다(새 행을 만들지 않는다).
     *
     * - 마지막 확인 뒤에 리콜 DB 가 적재·갱신됐으면(recall.synced_at) 저장된 입력으로 다시 판정한다
     *   — 구매 뒤에 새로 공표된 리콜도 다시 스캔하면 잡힌다. 사진 확인(FOUND)을 했던 건은 그 결과까지 넣어 다시 채점한다.
     * - 아니면 판정은 그대로 두고 '마지막 확인 시각'(updated_at)만 갱신한다 — 이력 목록 맨 위로 올라온다.
     * verifyByManualInput 이 프록시로 부른다.
     */
    @Transactional
    public VerificationResultResponse reuseVerification(Long verificationId) {
        Verification verification = findOwned(verificationId);
        LocalDateTime latestSync = recallRepository.findLatestSyncedAt();
        LocalDateTime checked = verification.getUpdatedAt() != null
                ? verification.getUpdatedAt() : verification.getCreatedAt();
        if (latestSync != null && checked != null && latestSync.isAfter(checked)) {
            rejudge(verification);
        }
        verification.touch();
        log.info("[Verification] 같은 입력 — 기존 검증 {} 을(를) 다시 씀(새로 쌓지 않음)", verificationId);
        return getResult(verificationId);
    }

    /** 10/31 — 저장된 입력(extraction)으로 다시 판정한다. 판단 제외(해외직구)·빈 입력은 그대로 둔다. */
    private void rejudge(Verification verification) {
        Long id = verification.getId();
        Extraction primary = extractionService.primaryRow(id);
        String rawText = primary == null ? null : primary.getRawText();
        if (ExtractionNotes.excludedOverseas(rawText)) {
            return;
        }
        ExtractedProduct product = extractionService.loadMerged(id);
        if (product == null || product.isEmpty()) {
            return;
        }
        MatchOutcome outcome;
        if (ExtractionNotes.imageCheck(rawText) == ExtractionNotes.ImageCheck.FOUND) {
            outcome = matchingService.matchByImage(id, product, extractionService.loadImageInsight(id));
        } else {
            matchResultRepository.deleteByVerificationId(id);
            outcome = matchingService.matchWithImage(id, product);
        }
        List<MatchCandidate> candidates = outcome.candidates();
        recordEvidence(id, candidates);
        FinalResult result = candidates.isEmpty() ? FinalResult.NO_MATCH : toFinalResult(candidates.get(0).decision());
        log.info("[Verification] 리콜 DB 갱신 뒤 다시 판정 — 검증 {}: {} → {}", id, verification.getFinalResult(), result);
        verification.complete(result);
    }

    /** 10/31 — 대표 후보의 항목별 대조표를 판정 당시 값으로 남긴다(판정 근거 스냅샷). 후보가 없으면 지운다 */
    private void recordEvidence(Long verificationId, List<MatchCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            extractionService.saveEvidence(verificationId, null, null);
            return;
        }
        MatchCandidate best = candidates.get(0);
        extractionService.saveEvidence(verificationId, best.recallUid(), best.comparisons());
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
     * 9/27 추가 — 사진으로 찾기. 사용자가 버튼을 눌렀을 때만 Google Vision 을 부른다.
     *
     * ── 10/10 최종 흐름 2차(팀장 결정) ──
     * 1차(KC 인증번호 + 제품명)로 판단할 수 없을 때 — KC 번호를 못 얻었거나 적힌 번호가 KC 인증 DB 에 없을 때 —
     * 썸네일을 Vision 으로 웹에서 찾는다. 같은 사진이 실린 문서에서 <b>여러 사이트에 반복된 값</b>만 가져온다:
     *   상품명(페이지 제목), KC 인증번호·모델명(페이지 본문 — GoogleVisionClient 가 페이지를 열어 뽑는다).
     * 그 값으로 공표문 후보를 더 찾고 다시 채점한다(MatchingService.matchByImage). 결과는 최대 '의심'이다.
     * 반복된 값이 하나도 없으면 "사진으로도 못 찾음"(NONE)으로 남긴다.
     *
     * 이미 사진 확인을 한 건(FOUND/NONE)은 Vision 을 다시 부르지 않고 현재 결과를 돌려준다 — 사용량 보호.
     * 판단 제외(해외직구·구매대행) 건은 사진 확인도 하지 않는다.
     *
     * 10/10 — Vision 호출과 웹페이지 읽기(최대 십수 초)는 트랜잭션 밖에서 한다. 기다리는 동안 DB 커넥션을
     * 잡고 있지 않게 하려는 것(10/3 KC 조회와 같은 이유). 저장·재채점은 applyImageCheck 가 트랜잭션으로 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerificationResultResponse checkByImage(Long verificationId) {
        findOwned(verificationId);

        var primary = extractionService.primaryRow(verificationId);
        String rawText = primary == null ? null : primary.getRawText();
        ExtractionNotes.ImageCheck done = ExtractionNotes.imageCheck(rawText);
        if (done == ExtractionNotes.ImageCheck.FOUND || done == ExtractionNotes.ImageCheck.NONE
                || ExtractionNotes.excludedOverseas(rawText)) {
            return self.getResult(verificationId);
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
        return self.applyImageCheck(verificationId, product, insight);
    }

    /**
     * 10/10 — 사진 확인 결과 저장과 재채점(트랜잭션). checkByImage 가 Vision 호출을 끝낸 뒤 프록시로 부른다.
     * 외부에서 직접 부르지 말 것.
     */
    @Transactional
    public VerificationResultResponse applyImageCheck(Long verificationId, ExtractedProduct product,
                                                      ImageInsight insight) {
        Verification verification = findOwned(verificationId);

        // 10/10 — 같은 건을 두 탭에서 거의 동시에 눌렀으면 먼저 끝난 쪽 결과를 그대로 쓴다(중복 저장·재채점 방지).
        var primary = extractionService.primaryRow(verificationId);
        ExtractionNotes.ImageCheck done = ExtractionNotes.imageCheck(primary == null ? null : primary.getRawText());
        if (done == ExtractionNotes.ImageCheck.FOUND || done == ExtractionNotes.ImageCheck.NONE) {
            return getResult(verificationId);
        }

        if (!insight.isUsable()) {
            // 웹에서 이 사진을 못 찾았거나, 찾았어도 여러 사이트에서 반복된 상품명·KC 번호·모델명이 없다.
            // 텍스트 결과를 그대로 두고 "사진으로도 못 찾음"만 남긴다.
            extractionService.markImageCheck(verificationId, ExtractionNotes.ImageCheck.NONE);
            return getResult(verificationId);
        }

        extractionService.saveImageInsight(verificationId, insight);
        extractionService.markImageCheck(verificationId, ExtractionNotes.ImageCheck.FOUND);

        MatchOutcome outcome = matchingService.matchByImage(verificationId, product, insight);
        List<MatchCandidate> candidates = outcome.candidates();
        recordEvidence(verificationId, candidates);
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
        // 10/31 — 판정 때 남긴 스냅샷이 지금 대표 후보 것이면 그대로 쓴다(규칙이 바뀌어도 과거 근거가 그대로 보인다).
        // 10/31 이전 검증·스냅샷이 없는 건은 예전처럼 다시 계산한다.
        Extraction primary = extractionService.primaryRow(verificationId);
        ExtractionNotes.EvidenceSnapshot snapshot =
                ExtractionNotes.evidence(primary == null ? null : primary.getRawText());
        if (snapshot != null && snapshot.recallUid().equals(recall.getRecallUid())) {
            return snapshot.rows();
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
        // 10/31 — 마지막으로 확인한 순서(updated_at). 같은 입력을 다시 확인하면 그 검증이 맨 위로 온다
        Page<Verification> found = channel == null
                ? verificationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId, PageRequest.of(page, size))
                : verificationRepository.findByUserIdAndChannelOrderByUpdatedAtDescIdDesc(
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
                    kcLookupName(rawText), kcCertState(rawText),
                    v.getUpdatedAt() != null ? v.getUpdatedAt() : v.getCreatedAt());
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
                extraction == null ? null : extraction.getThumbnailUrl(),
                extraction == null ? null : extraction.getCertNum());
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
        // 10/31 — URL 검증도 뽑은 상품명이 있으면 상품명을 먼저 보여 준다(주소는 상품명이 없을 때만)
        if (extraction != null && extraction.getProductName() != null) {
            return extraction.getProductName();
        }
        if (v.getInputUrl() != null) {
            return v.getInputUrl();
        }
        if (v.getImagePath() != null) {
            return "사진 업로드";
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

    /** 10/31 — 결과 화면 '내가 입력한 제품'용 저장된 입력값. 리콜 후보가 없어도 채운다(헝겊책 실측: 전부 '-'로 나왔음). */
    private ProductInputResponse inputOf(Verification v) {
        return ProductInputResponse.of(extractionService.primaryRow(v.getId()));
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
                kcOf(v), rv.imageProductName(), rv.imageCertNum(), rv.imageModelName(),
                inputOf(v), r == null ? null : r.getCertNum());
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
                kcOf(v), rv.imageProductName(), rv.imageCertNum(), rv.imageModelName(),
                inputOf(v), r == null ? null : r.getCertNum());
    }
}
