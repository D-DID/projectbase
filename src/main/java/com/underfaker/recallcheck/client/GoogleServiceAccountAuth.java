package com.underfaker.recallcheck.client;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google 서비스 계정 JSON 키로 OAuth2 액세스 토큰을 발급받는다.
 *
 * ── 왜 직접 만들었나 (9/24) ──
 * 정석은 com.google.auth:google-auth-library-oauth2-http 를 쓰는 것이다.
 * 안 쓴 이유는 두 가지다.
 *   (1) 의존성을 하나 늘리면 팀원 전원이 Gradle 동기화를 다시 해야 하고,
 *       30일 데모 직전에 빌드가 깨질 여지를 만든다.
 *   (2) 필요한 건 "JWT 서명 → 토큰 교환" 두 단계뿐이다. JDK 표준 API 로 충분하다.
 *
 * ObjectMapper 도 쓰지 않는다. Spring Boot 4 는 Jackson 3(tools.jackson)을 기본으로 쓰고
 * 이 프로젝트는 어느 쪽 ObjectMapper 가 올라올지 확정하지 않았다
 * (CustomAuthenticationEntryPoint 주석과 같은 이유). 서비스 계정 JSON 은 구글이 기계적으로
 * 만든 평평한 객체라, 필요한 문자열 필드 몇 개만 정규식으로 꺼낸다.
 *
 * ── 흐름 ──
 *   1. JSON 파일에서 client_email, private_key, private_key_id, token_uri 를 읽는다.
 *   2. RS256 으로 서명한 JWT 를 만든다 (iss=client_email, aud=token_uri, 유효 1시간).
 *   3. token_uri 에 grant_type=jwt-bearer 로 보내 access_token 을 받는다.
 *   4. 만료 60초 전까지 재사용한다. 토큰 발급은 Vision 호출 건수에 안 잡힌다.
 *
 * ── 보안 ──
 *   이 JSON 에는 개인키가 들어 있다. 저장소 안에 두지 말 것.
 *   로그에는 client_email 과 key id 앞 8자만 남긴다. 토큰·개인키는 절대 찍지 않는다.
 */
public class GoogleServiceAccountAuth {

    /** Vision 만 쓰므로 범위를 최소로 잡는다. */
    public static final String SCOPE_CLOUD_VISION = "https://www.googleapis.com/auth/cloud-vision";

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    /** 만료 직전 토큰을 들고 호출하다 401 이 나는 걸 막는 여유. */
    private static final long REFRESH_MARGIN_SECONDS = 60;

    /** 토큰 교환 HTTP 호출을 갈아끼울 수 있게 둔다 — 테스트에서 네트워크 없이 검증하려고. */
    @FunctionalInterface
    public interface TokenTransport {
        /** @return 응답 본문. HTTP 오류면 예외를 던진다. */
        String post(String tokenUri, String formBody) throws IOException;
    }

    private final String clientEmail;
    private final String privateKeyId;
    private final String tokenUri;
    private final PrivateKey privateKey;
    private final String scope;
    private final TokenTransport transport;
    private final Clock clock;

    private String cachedToken;
    private long cachedTokenExpiresAtEpochSec;

    /** 실제 운영용 — JDK HttpClient 로 토큰을 교환한다. */
    public static GoogleServiceAccountAuth fromFile(Path jsonPath) throws IOException {
        return fromJson(Files.readString(jsonPath, StandardCharsets.UTF_8),
                SCOPE_CLOUD_VISION, defaultTransport(), Clock.systemUTC());
    }

    /** 테스트용 — 전송 계층과 시계를 주입한다. */
    public static GoogleServiceAccountAuth fromJson(String json, String scope,
                                                    TokenTransport transport, Clock clock) {
        String type = stringField(json, "type");
        if (type != null && !"service_account".equals(type)) {
            throw new IllegalArgumentException(
                    "서비스 계정 키 파일이 아니다 (type=" + type + "). "
                            + "콘솔 > IAM 및 관리자 > 서비스 계정 > 키 > 새 키 만들기 > JSON 으로 받은 파일이어야 한다.");
        }
        String email = require(json, "client_email");
        String pem = require(json, "private_key");
        String keyId = stringField(json, "private_key_id");
        String uri = stringField(json, "token_uri");
        return new GoogleServiceAccountAuth(email, keyId, uri == null ? DEFAULT_TOKEN_URI : uri,
                parsePkcs8Pem(pem), scope, transport, clock);
    }

    GoogleServiceAccountAuth(String clientEmail, String privateKeyId, String tokenUri,
                             PrivateKey privateKey, String scope,
                             TokenTransport transport, Clock clock) {
        this.clientEmail = clientEmail;
        this.privateKeyId = privateKeyId;
        this.tokenUri = tokenUri;
        this.privateKey = privateKey;
        this.scope = scope;
        this.transport = transport;
        this.clock = clock;
    }

    public String clientEmail() {
        return clientEmail;
    }

    /** 로그용 — key id 앞 8자. 콘솔의 '키' 열 값과 대조할 때 쓴다. */
    public String keyIdPrefix() {
        return privateKeyId == null ? "?" : privateKeyId.substring(0, Math.min(8, privateKeyId.length()));
    }

    /**
     * 유효한 액세스 토큰. 캐시가 살아 있으면 재사용한다.
     *
     * @throws IOException 토큰 교환 실패(네트워크·권한·키 폐기 등)
     */
    public synchronized String accessToken() throws IOException {
        long now = clock.instant().getEpochSecond();
        if (cachedToken != null && now < cachedTokenExpiresAtEpochSec - REFRESH_MARGIN_SECONDS) {
            return cachedToken;
        }

        String assertion = signedJwt(now);
        String form = "grant_type=" + urlEncode(GRANT_TYPE) + "&assertion=" + urlEncode(assertion);
        String body = transport.post(tokenUri, form);

        String token = stringField(body, "access_token");
        if (token == null || token.isBlank()) {
            String error = stringField(body, "error_description");
            if (error == null) {
                error = stringField(body, "error");
            }
            throw new IOException("토큰 응답에 access_token 이 없다: " + (error == null ? "(사유 없음)" : error));
        }
        Long expiresIn = longField(body, "expires_in");

        cachedToken = token;
        cachedTokenExpiresAtEpochSec = now + (expiresIn == null ? TOKEN_LIFETIME_SECONDS : expiresIn);
        return token;
    }

    /** 401 을 받았을 때 호출부가 캐시를 버리게 한다. */
    public synchronized void invalidate() {
        cachedToken = null;
        cachedTokenExpiresAtEpochSec = 0;
    }

    // ------------------------------------------------------------------ JWT

    String signedJwt(long nowEpochSec) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\""
                + (privateKeyId == null ? "" : ",\"kid\":\"" + jsonEscape(privateKeyId) + "\"")
                + "}";
        String claims = "{"
                + "\"iss\":\"" + jsonEscape(clientEmail) + "\","
                + "\"scope\":\"" + jsonEscape(scope) + "\","
                + "\"aud\":\"" + jsonEscape(tokenUri) + "\","
                + "\"iat\":" + nowEpochSec + ","
                + "\"exp\":" + (nowEpochSec + TOKEN_LIFETIME_SECONDS)
                + "}";

        String signingInput = b64url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + b64url(claims.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64url(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명 실패: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 키 파싱

    /**
     * "-----BEGIN PRIVATE KEY-----" PEM → PrivateKey.
     * JSON 안에서는 줄바꿈이 \n 이스케이프로 들어 있다. stringField 가 이미 풀어 주지만,
     * 이스케이프가 그대로 남은 문자열이 와도 되게 둘 다 처리한다.
     */
    static PrivateKey parsePkcs8Pem(String pem) {
        String base64 = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (base64.isEmpty()) {
            throw new IllegalArgumentException("private_key 가 비어 있다");
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("private_key 를 읽을 수 없다 (PKCS#8 RSA 가 아님): "
                    + e.getClass().getSimpleName(), e);
        }
    }

    // ------------------------------------------------------------------ 최소 JSON 추출

    /**
     * 평평한 JSON 객체에서 문자열 필드 하나를 꺼내 이스케이프를 푼다. 없으면 null.
     *
     * 정규식이 아니라 손으로 훑는다. 처음엔 "((?:[^\"\\\\]|\\\\.)*)" 정규식을 썼는데,
     * java.util.regex 는 이 반복을 글자마다 재귀로 처리해서 1,700자짜리 private_key 에서
     * StackOverflowError 가 났다(9/24 단위 테스트에서 발견). 실제 키 파일을 넣는 순간
     * 앱 기동 때 터졌을 버그다.
     */
    static String stringField(String json, String name) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + name + "\"";
        int from = 0;
        while (true) {
            int k = json.indexOf(needle, from);
            if (k < 0) {
                return null;
            }
            int i = k + needle.length();
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length() || json.charAt(i) != ':') {
                from = k + 1;          // 값 안에 같은 글자가 있었던 경우 — 다음을 찾는다
                continue;
            }
            i++;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length() || json.charAt(i) != '"') {
                return null;           // 문자열이 아닌 값(숫자·객체)
            }
            int start = ++i;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    return jsonUnescape(json.substring(start, i));
                }
                i++;
            }
            return null;               // 닫는 따옴표 없음
        }
    }

    static Long longField(String json, String name) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private static String require(String json, String name) {
        String v = stringField(json, name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("서비스 계정 JSON 에 " + name + " 필드가 없다");
        }
        return v;
    }

    static String jsonUnescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '/' -> out.append('/');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 전송

    private static TokenTransport defaultTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.getDefault())
                .build();
        return (uri, form) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    String error = stringField(response.body(), "error_description");
                    throw new IOException("토큰 교환 HTTP " + response.statusCode()
                            + (error == null ? "" : " — " + error));
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("토큰 교환이 중단됐다", e);
            }
        };
    }
}
