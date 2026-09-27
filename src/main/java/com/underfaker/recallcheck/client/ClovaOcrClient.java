package com.underfaker.recallcheck.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 네이버 클라우드 CLOVA OCR — General(Text) OCR.
 *
 *   POST {invoke-url}
 *   Header  X-OCR-SECRET: {secret-key}
 *   Body    {"version":"V2","requestId":"...","timestamp":...,"lang":"ko",
 *            "images":[{"format":"jpg","name":"...","data":"<base64>"}]}
 *   Response {"images":[{"inferResult":"SUCCESS","fields":[{"inferText":"...","inferConfidence":0.99}]}]}
 *
 * 이미지는 두 방법으로 보낼 수 있다.
 *   1. data  — 파일을 base64 로 실어 보낸다. 사용자가 업로드한 이미지에 쓴다.
 *   2. url   — 외부에서 접근 가능한 이미지 주소를 넘긴다. 쿠팡 썸네일처럼 이미 공개된
 *              이미지는 이쪽이 낫다. 우리 서버가 이미지를 받아 다시 올릴 필요가 없다.
 *
 * ── 호출을 막는 장치가 두 겹이다 ──
 * 1. 키가 없으면 비활성. isEnabled() 가 false 면 HTTP 요청을 만들지 않는다.
 *    예외를 던지지 않으므로 팀원들이 키 없이도 앱을 띄우고 나머지를 개발할 수 있다.
 * 2. 월 건수 상한. OcrCallBudget 이 이번 달 호출 수를 세고 상한에 닿으면 막는다.
 *    9/21 추가 — 키가 팀원 계정이라 우리 실수가 남의 청구서로 간다. "조심하자"는
 *    대책이 아니어서 코드로 막았다.
 *
 * 자동 재시도는 없다. 실패를 조용히 재시도하면 할당량이 순식간에 녹는다.
 * 실패는 로그에 남기고 그대로 반환한다.
 */
@Slf4j
@Component
public class ClovaOcrClient {

    /** API 버전. V2 는 fields 에 신뢰도·줄바꿈 정보가 함께 온다. */
    private static final String API_VERSION = "V2";
    private static final String LANG_KO = "ko";

    private final RestClient restClient;
    private final OcrCallBudget budget;
    private final String invokeUrl;
    private final String secretKey;

    /**
     * RestClient 를 직접 만든다.
     *
     * 9/21 수정 — 처음엔 RestClient.Builder 를 주입받게 했는데 이 프로젝트에는 그 빈이 없다.
     * RestClientConfig 가 safetyKoreaRestClient / ocrRestClient 를 정적 RestClient.builder() 로
     * 만들고 Builder 자체를 빈으로 내놓지 않기 때문이다. 같은 방식을 따른다.
     *
     * baseUrl 을 안 잡는 이유: CLOVA 는 도메인마다 invoke-url 이 통째로 다른 전체 URL 이라
     * 호출 때 그 URL 을 그대로 uri() 에 넣는 쪽이 맞다.
     */
    public ClovaOcrClient(OcrCallBudget budget,
                          @Value("${clova.ocr.invoke-url:}") String invokeUrl,
                          @Value("${clova.ocr.secret-key:}") String secretKey) {
        this.budget = budget;
        this.invokeUrl = invokeUrl == null ? "" : invokeUrl.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.restClient = RestClient.builder().build();

        if (!isEnabled()) {
            log.warn("[ClovaOcr] invoke-url 또는 secret-key 가 비어 있어 OCR 을 사용하지 않는다. "
                    + "환경변수 CLOVA_OCR_INVOKE_URL / CLOVA_OCR_SECRET_KEY 를 설정할 것.");
        } else {
            log.info("[ClovaOcr] 활성 — 이번 달 잔여 {}건", budget.remaining(OcrCallBudget.CLOVA));
        }
    }

    /** 키가 설정돼 있어 호출할 수 있는 상태인가 */
    public boolean isEnabled() {
        return !invokeUrl.isBlank() && !secretKey.isBlank();
    }

    /** 지금 호출해도 되는가 — 키도 있고 이번 달 상한도 안 찼는가. */
    public boolean isAvailable() {
        return isEnabled() && budget.remaining(OcrCallBudget.CLOVA) > 0;
    }

    /**
     * 로컬 파일에서 텍스트를 읽는다.
     *
     * @param imagePath 서버에 저장된 이미지 경로
     * @return 인식된 전체 텍스트. 비활성·상한초과·실패·인식결과없음이면 Optional.empty()
     */
    public Optional<String> recognizeFile(Path imagePath) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(imagePath);
        } catch (IOException e) {
            log.warn("[ClovaOcr] 이미지 파일을 읽지 못했다: {} ({})", imagePath, e.getMessage());
            return Optional.empty();
        }
        String fileName = imagePath.getFileName().toString();
        OcrImage image = new OcrImage(
                extensionOf(fileName), fileName, Base64.getEncoder().encodeToString(bytes), null);
        return call(image);
    }

    /**
     * 이미지 URL 에서 텍스트를 읽는다.
     *
     * 쿠팡 썸네일처럼 이미 공개된 이미지에 쓴다. CLOVA 서버가 그 URL 로 직접 접근하므로
     * 로그인이 필요하거나 리퍼러를 검사하는 이미지는 실패한다 — 그 경우 파일로 받아서
     * recognizeFile() 을 쓰는 수밖에 없다.
     */
    public Optional<String> recognizeUrl(String imageUrl) {
        if (!isEnabled() || imageUrl == null || imageUrl.isBlank()) {
            return Optional.empty();
        }
        OcrImage image = new OcrImage(
                extensionOf(imageUrl), "thumbnail", null, imageUrl.trim());
        return call(image);
    }

    private Optional<String> call(OcrImage image) {
        // ── 과금 차단선 ──
        // 여기를 통과하지 못하면 아래 HTTP 요청 코드에 도달하지 않는다.
        // 요청을 만들기 '전에' 세는 이유: 실패한 호출도 제공자 쪽에서는 호출로 잡힐 수
        // 있어서, 성공한 것만 세면 실제 사용량보다 적게 세게 된다.
        if (!budget.tryAcquire(OcrCallBudget.CLOVA)) {
            return Optional.empty();
        }

        OcrRequest request = new OcrRequest(
                API_VERSION, UUID.randomUUID().toString(), System.currentTimeMillis(),
                LANG_KO, List.of(image));

        OcrResponse response;
        try {
            response = restClient.post()
                    .uri(invokeUrl)
                    .header("X-OCR-SECRET", secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OcrResponse.class);
        } catch (RuntimeException e) {
            // 재시도하지 않는다 — 무료 할당량이 월 100건이라 실패 재시도가 곧 소진이다.
            log.warn("[ClovaOcr] 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }

        if (response == null || response.images() == null || response.images().isEmpty()) {
            log.warn("[ClovaOcr] 응답이 비어 있다");
            return Optional.empty();
        }

        OcrImageResult result = response.images().get(0);
        if (!"SUCCESS".equalsIgnoreCase(result.inferResult())) {
            log.warn("[ClovaOcr] 인식 실패 — inferResult={} message={}",
                    result.inferResult(), result.message());
            return Optional.empty();
        }
        if (result.fields() == null || result.fields().isEmpty()) {
            return Optional.empty();
        }

        // General OCR 은 텍스트를 단어 단위로 쪼개 준다. lineBreak 가 true 인 필드 뒤에서
        // 줄을 바꿔 줘야 "인증번호 : XX-1234" 같은 라벨·값 구조가 살아남는다.
        StringBuilder text = new StringBuilder();
        for (OcrField field : result.fields()) {
            if (field.inferText() == null || field.inferText().isBlank()) {
                continue;
            }
            text.append(field.inferText().trim());
            text.append(Boolean.TRUE.equals(field.lineBreak()) ? "\n" : " ");
        }

        String joined = text.toString().trim();
        log.debug("[ClovaOcr] 인식 {}자, 필드 {}개", joined.length(), result.fields().size());
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    /** 파일명·URL 에서 확장자를 뽑는다. 못 찾으면 jpg 로 둔다(CLOVA 는 실제 포맷을 스스로 판별한다). */
    private String extensionOf(String nameOrUrl) {
        String cleaned = nameOrUrl;
        int query = cleaned.indexOf('?');
        if (query > 0) {
            cleaned = cleaned.substring(0, query);
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            return "jpg";
        }
        String ext = cleaned.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg", "png", "pdf", "tiff", "tif" -> ext;
            default -> "jpg";
        };
    }

    // ------------------------------------------------------------------ 요청·응답 DTO

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OcrRequest(String version, String requestId, long timestamp,
                      String lang, List<OcrImage> images) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OcrImage(String format, String name, String data, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OcrResponse(String version, String requestId, Long timestamp,
                       List<OcrImageResult> images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OcrImageResult(String uid, String name, String inferResult,
                          String message, List<OcrField> fields) {
    }

    /**
     * @param inferText       인식된 단어
     * @param inferConfidence 신뢰도 0.0 ~ 1.0
     * @param lineBreak       이 단어 뒤에서 줄이 바뀌는지. V2 응답에만 있다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OcrField(String inferText, Double inferConfidence, Boolean lineBreak) {
    }
}
