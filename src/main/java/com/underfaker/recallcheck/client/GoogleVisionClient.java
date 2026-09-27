package com.underfaker.recallcheck.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Google Cloud Vision — WEB_DETECTION 전용 클라이언트.
 *
 *   POST {google.vision.url}?key={api-key}                 ← (A) API 키
 *   POST {google.vision.url}  Authorization: Bearer {토큰}  ← (B) 서비스 계정
 *   Body {"requests":[{"image":{"source":{"imageUri":"..."}},
 *                      "features":[{"type":"WEB_DETECTION","maxResults":10}]}]}
 *
 * ── 인증 두 가지 (9/24) ──
 *   (B) google.vision.credentials-path 에 서비스 계정 JSON 경로가 있으면 그걸 쓴다.
 *   (A) 없으면 google.vision.api-key 를 쓴다.
 *   둘 다 없거나 형식이 틀리면 비활성 — 예외 없이 텍스트 판정만 돈다.
 *
 *   api-key 형식을 검사하는 이유: 9/24 에 서비스 계정 '키 ID'(40자 16진수)가 api-key 자리에
 *   들어간 적이 있다. 그대로 두면 호출마다 400 이 나면서 월 상한 카운터만 깎인다.
 *   API 키는 'AIza' 로 시작하므로, 그게 아니면 호출 자체를 막고 기동 로그에 이유를 남긴다.
 *
 * WEB_DETECTION 만 쓰는 이유는 ImageInsight 주석 참조 — Vision 에는 이미지 대 이미지
 * 유사도 기능이 없고, Lens 는 공개 API 가 없다. 역이미지 검색으로 얻은 <b>텍스트</b>를
 * 기존 매칭 엔진에 태우는 것이 이 프로젝트에서 실제로 만들 수 있는 유일한 형태다.
 *
 * ── 호출을 막는 장치가 두 겹이다 (ClovaOcrClient 와 같은 구조) ──
 * 1. 키가 없으면 비활성. isEnabled() 가 false 면 HTTP 요청을 만들지 않는다.
 *    예외를 던지지 않으므로 팀원들이 키 없이도 앱을 띄우고 나머지를 개발할 수 있다.
 * 2. 월 건수 상한. OcrCallBudget.VISION — 무료 1,000건 중 800 에서 끊는다.
 *
 * 과금 정보(2026-09 기준 확인):
 *   Web Detection 은 월 1,000 유닛 무료, 초과분 1,000 유닛당 $3.50.
 *   기능별로 따로 센다. 자동 과금이므로 무료 한도만 믿으면 안 되고,
 *   프로젝트에 결제 계정이 붙어 있어야 무료 한도도 쓸 수 있다.
 *
 * 자동 재시도는 없다. 실패를 조용히 재시도하면 할당량이 녹는다.
 */
@Slf4j
@Component
public class GoogleVisionClient {

    private static final String FEATURE_WEB_DETECTION = "WEB_DETECTION";

    /** 응답에서 받아 올 최대 결과 수. 더 받아도 하위는 점수가 낮아 쓸모가 없다. */
    private static final int MAX_RESULTS = 10;

    /** ImageInsight 에 담을 최대 개체 수. 대조 비용이 후보 수에 비례한다. */
    private static final int MAX_ENTITIES = 5;

    /**
     * base64 로 실어 보낼 수 있는 최대 이미지 크기.
     * Vision 요청 본문 상한은 10MB 이고 base64 는 약 1.33배로 불어나므로 4MB 에서 끊는다.
     */
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    /** 인증 방식. 기동 시 한 번 정해진다. */
    public enum AuthMode { SERVICE_ACCOUNT, API_KEY, DISABLED }

    private final RestClient restClient;
    private final OcrCallBudget budget;
    private final String endpoint;
    private final String apiKey;
    private final GoogleServiceAccountAuth serviceAccount;
    private final AuthMode mode;

    /**
     * RestClient 를 직접 만든다 — 이 프로젝트에는 RestClient.Builder 빈이 없다.
     * ClovaOcrClient 9/21 주석과 같은 이유.
     */
    public GoogleVisionClient(
            OcrCallBudget budget,
            @Value("${google.vision.url:https://vision.googleapis.com/v1/images:annotate}") String endpoint,
            @Value("${google.vision.api-key:}") String apiKey,
            @Value("${google.vision.credentials-path:}") String credentialsPath) {

        this.budget = budget;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = RestClient.builder().build();

        this.serviceAccount = loadServiceAccount(credentialsPath);
        this.mode = decideMode(this.endpoint, this.apiKey, this.serviceAccount);

        switch (mode) {
            case SERVICE_ACCOUNT -> log.info("[Vision] 활성 (서비스 계정 {} / key {}…) — 이번 달 잔여 {}건",
                    serviceAccount.clientEmail(), serviceAccount.keyIdPrefix(),
                    budget.remaining(OcrCallBudget.VISION));
            case API_KEY -> log.info("[Vision] 활성 (API 키) — 이번 달 잔여 {}건",
                    budget.remaining(OcrCallBudget.VISION));
            case DISABLED -> log.warn("[Vision] 비활성 — {} 판정은 텍스트 단계까지만 수행된다.",
                    disabledReason(this.apiKey, credentialsPath));
        }
    }

    /**
     * 인증 방식 결정. 서비스 계정이 우선이다 — 파일이 명시적으로 지정됐다는 건
     * 그걸 쓰겠다는 의도로 본다.
     */
    static AuthMode decideMode(String endpoint, String apiKey, GoogleServiceAccountAuth serviceAccount) {
        if (endpoint == null || endpoint.isBlank()) {
            return AuthMode.DISABLED;
        }
        if (serviceAccount != null) {
            return AuthMode.SERVICE_ACCOUNT;
        }
        if (looksLikeApiKey(apiKey)) {
            return AuthMode.API_KEY;
        }
        return AuthMode.DISABLED;
    }

    /** @see GoogleKeyFormat#looksLikeApiKey */
    static boolean looksLikeApiKey(String key) {
        return GoogleKeyFormat.looksLikeApiKey(key);
    }

    /** @see GoogleKeyFormat#looksLikeKeyId */
    static boolean looksLikeKeyId(String key) {
        return GoogleKeyFormat.looksLikeKeyId(key);
    }

    private static String disabledReason(String apiKey, String credentialsPath) {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            return "google.vision.credentials-path 파일을 읽지 못했다(위 로그 참조).";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "google.vision.api-key / credentials-path 둘 다 비어 있다.";
        }
        if (looksLikeKeyId(apiKey)) {
            return "google.vision.api-key 에 들어간 값은 API 키가 아니라 서비스 계정 '키 ID'로 보인다 "
                    + "(16진수 40자). 키 ID 는 이름표라 인증에 못 쓴다. "
                    + "API 및 서비스 > 사용자 인증 정보 > API 키 를 만들어 'AIza…' 값을 넣거나, "
                    + "서비스 계정 JSON 파일 경로를 google.vision.credentials-path 에 넣을 것.";
        }
        return "google.vision.api-key 가 API 키 형식('AIza…')이 아니다. 호출해도 400 만 나므로 막아 둔다.";
    }

    private static GoogleServiceAccountAuth loadServiceAccount(String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return null;
        }
        Path path = Path.of(credentialsPath.trim());
        if (!Files.isRegularFile(path)) {
            log.error("[Vision] credentials-path 파일이 없다: {}", path.toAbsolutePath());
            return null;
        }
        try {
            return GoogleServiceAccountAuth.fromFile(path);
        } catch (IOException | RuntimeException e) {
            // 개인키 내용이 메시지에 섞이지 않게 예외 종류와 메시지만 남긴다.
            log.error("[Vision] 서비스 계정 JSON 을 읽지 못했다: {} ({})", path.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    public AuthMode authMode() {
        return mode;
    }

    /** 인증 수단이 설정돼 있어 호출할 수 있는 상태인가 */
    public boolean isEnabled() {
        return mode != AuthMode.DISABLED;
    }

    /** 지금 호출해도 되는가 — 키도 있고 이번 달 상한도 안 찼는가. */
    public boolean isAvailable() {
        return isEnabled() && budget.remaining(OcrCallBudget.VISION) > 0;
    }

    /**
     * 이미지 URL 로 판독한다.
     *
     * Google 서버가 그 URL 에 직접 접근한다. 쿠팡 CDN 썸네일처럼 공개된 이미지는 대개 되지만,
     * 리퍼러를 검사하거나 로그인이 필요한 이미지는 실패한다. 그 경우 annotateBytes 를 쓴다.
     *
     * @return 판독 결과. 비활성·상한초과·실패·결과없음이면 {@link ImageInsight#NONE}
     */
    public ImageInsight annotateUrl(String imageUrl) {
        if (!isEnabled() || imageUrl == null || imageUrl.isBlank()) {
            return ImageInsight.NONE;
        }
        VisionImage image = new VisionImage(null, new VisionSource(imageUrl.trim()));
        return call(image, imageUrl);
    }

    /**
     * 이미지 바이트로 판독한다. 사용자가 업로드한 이미지에 쓴다.
     *
     * @return 판독 결과, 또는 {@link ImageInsight#NONE}
     */
    public ImageInsight annotateBytes(byte[] imageBytes) {
        if (!isEnabled() || imageBytes == null || imageBytes.length == 0) {
            return ImageInsight.NONE;
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            log.warn("[Vision] 이미지가 너무 크다 — {}바이트 (상한 {}). 판독을 건너뛴다.",
                    imageBytes.length, MAX_IMAGE_BYTES);
            return ImageInsight.NONE;
        }
        VisionImage image = new VisionImage(
                Base64.getEncoder().encodeToString(imageBytes), null);
        return call(image, "<" + imageBytes.length + " bytes>");
    }

    /**
     * URL 로 먼저 시도하고, 결과가 쓸모없으면 바이트로 한 번 더 시도한다.
     *
     * 호출이 최대 두 번 나간다 — 상한을 두 칸 먹는다. 쿠팡 썸네일이 imageUri 로
     * 되는지 아직 확인되지 않아서 남겨 둔 경로이고, URL 방식이 된다고 확인되면
     * 호출부에서 annotateUrl 만 쓰는 쪽이 맞다.
     */
    public ImageInsight annotateWithFallback(String imageUrl, byte[] imageBytes) {
        ImageInsight byUrl = annotateUrl(imageUrl);
        if (byUrl.isUsable()) {
            return byUrl;
        }
        return annotateBytes(imageBytes);
    }

    /**
     * 9/27 — 호출 실패(토큰·상한·네트워크·API 오류·빈 응답)는 ImageInsight.FAILED, 웹에서 못 찾은 건 NONE.
     * 사진 확인(사용자 클릭)이 실패를 "못 찾음"으로 기록하지 않게 구분한다.
     */
    private ImageInsight call(VisionImage image, String label) {
        // 서비스 계정이면 토큰부터 받는다. 토큰 발급은 Vision 호출 건수가 아니므로
        // 상한 카운터를 깎기 전에 한다 — 토큰 실패로 카운터만 줄어드는 일을 막는다.
        String bearer = null;
        if (mode == AuthMode.SERVICE_ACCOUNT) {
            try {
                bearer = serviceAccount.accessToken();
            } catch (IOException | RuntimeException e) {
                log.warn("[Vision] 서비스 계정 토큰 발급 실패 — 호출하지 않는다: {}", e.getMessage());
                return ImageInsight.FAILED;
            }
        }

        // ── 과금 차단선 ──
        // 요청을 만들기 '전에' 센다. 실패한 호출도 제공자 쪽에서는 호출로 잡힐 수 있다.
        if (!budget.tryAcquire(OcrCallBudget.VISION)) {
            return ImageInsight.FAILED;
        }

        VisionRequest request = new VisionRequest(List.of(
                new AnnotateRequest(image,
                        List.of(new Feature(FEATURE_WEB_DETECTION, MAX_RESULTS)))));

        VisionResponse response;
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(mode == AuthMode.API_KEY ? endpoint + "?key=" + apiKey : endpoint)
                    .contentType(MediaType.APPLICATION_JSON);
            if (bearer != null) {
                spec = spec.header("Authorization", "Bearer " + bearer);
            }
            response = spec.body(request)
                    .retrieve()
                    .body(VisionResponse.class);
        } catch (RuntimeException e) {
            // 재시도하지 않는다. 메시지에 키가 섞여 나올 수 있어 가린다.
            String message = redact(e.getMessage());
            if (bearer != null && message != null && message.contains("401")) {
                // 키가 폐기됐거나 토큰이 만료됐다. 다음 호출 때 새로 받게 캐시를 비운다.
                serviceAccount.invalidate();
            }
            log.warn("[Vision] 호출 실패 ({}): {}", label, message);
            return ImageInsight.FAILED;
        }

        if (response == null || response.responses() == null || response.responses().isEmpty()) {
            log.warn("[Vision] 응답이 비어 있다 ({})", label);
            return ImageInsight.FAILED;
        }

        AnnotateResponse first = response.responses().get(0);
        if (first.error() != null && first.error().code() != null) {
            log.warn("[Vision] API 오류 — code={} message={}",
                    first.error().code(), redact(first.error().message()));
            return ImageInsight.FAILED;
        }

        WebDetection web = first.webDetection();
        if (web == null) {
            log.info("[Vision] webDetection 결과 없음 ({})", label);
            return ImageInsight.NONE;
        }

        String bestGuess = null;
        if (web.bestGuessLabels() != null && !web.bestGuessLabels().isEmpty()) {
            bestGuess = web.bestGuessLabels().get(0).label();
        }

        List<String> entities = new ArrayList<>();
        double topScore = 0.0;
        if (web.webEntities() != null) {
            for (WebEntity entity : web.webEntities()) {
                if (entity.description() == null || entity.description().isBlank()) {
                    continue;
                }
                double score = entity.score() == null ? 0.0 : entity.score();
                if (score < ImageInsight.MIN_ENTITY_SCORE) {
                    continue;
                }
                topScore = Math.max(topScore, score);
                if (!entities.contains(entity.description().trim())) {
                    entities.add(entity.description().trim());
                }
                if (entities.size() >= MAX_ENTITIES) {
                    break;
                }
            }
        }

        int pages = web.pagesWithMatchingImages() == null ? 0 : web.pagesWithMatchingImages().size();

        // 9/27 — 페이지 제목. 영어 라벨만으로는 한글 공표문과 대조가 안 된다(ImageInsight 주석 참조).
        List<String> titles = new ArrayList<>();
        if (web.pagesWithMatchingImages() != null) {
            for (WebPage page : web.pagesWithMatchingImages()) {
                String title = ImageInsight.cleanPageTitle(page.pageTitle());
                if (title != null && !titles.contains(title)) {
                    titles.add(title);
                }
                if (titles.size() >= ImageInsight.MAX_PAGE_TITLES) {
                    break;
                }
            }
        }

        ImageInsight insight = new ImageInsight(bestGuess, entities, topScore, pages, titles);
        log.info("[Vision] 판독 — bestGuess='{}' entities={} pages={} titles={}",
                bestGuess, entities, pages, titles);
        return insight;
    }

    /** 예외 메시지에 API 키가 실려 나오는 경우를 대비. 로그에 키를 남기지 않는다. */
    private String redact(String message) {
        if (message == null || apiKey.isBlank()) {
            return message;
        }
        return message.replace(apiKey, "<REDACTED>");
    }

    // ------------------------------------------------------------------ 요청 DTO

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VisionRequest(List<AnnotateRequest> requests) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnnotateRequest(VisionImage image, List<Feature> features) {
    }

    /** content(base64) 와 source(imageUri) 중 하나만 채운다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VisionImage(String content, VisionSource source) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VisionSource(String imageUri) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Feature(String type, Integer maxResults) {
    }

    // ------------------------------------------------------------------ 응답 DTO

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VisionResponse(List<AnnotateResponse> responses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnnotateResponse(WebDetection webDetection, VisionError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VisionError(Integer code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebDetection(List<WebEntity> webEntities,
                        List<BestGuessLabel> bestGuessLabels,
                        List<WebPage> pagesWithMatchingImages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebEntity(String entityId, Double score, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BestGuessLabel(String label, String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebPage(String url, String pageTitle) {
    }
}
