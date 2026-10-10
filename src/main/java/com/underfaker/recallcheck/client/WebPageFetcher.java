package com.underfaker.recallcheck.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 10/10 — 사진 찾기(2차)에서 같은 사진이 실린 웹페이지 HTML 을 받아 온다. JDK 만 쓴다(Spring 의존 없음 — 단위 테스트용).
 *
 * Vision 이 돌려준 아무 웹페이지나 서버가 여는 것이라 아래를 막는다(10/10 리뷰).
 *   - 내부망 주소: 첫 주소와 리다이렉트로 넘어가는 주소를 <b>매번</b> allowed 로 검사한다. 자동 리다이렉트는 끈다
 *     (자동으로 따라가면 공개 페이지가 169.254.169.254·localhost 로 302 를 걸어 검사를 건너뛸 수 있다).
 *   - 큰 응답·느린 응답: maxBytes 까지만 읽고, 읽는 중에도 deadline 을 넘으면 그만 읽는다.
 *     한 번의 read 는 readTimeoutMillis 를 넘지 않는다.
 * 예외를 던지지 않는다 — 못 받으면 null.
 */
public final class WebPageFetcher {

    /** 받은 페이지. contentType 은 없을 수 있다. */
    public record Page(byte[] body, String contentType) {
    }

    static final int MAX_REDIRECTS = 3;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxBytes;
    private final String userAgent;
    private final Predicate<URI> allowed;

    /**
     * @param allowed 열어도 되는 주소인가(GoogleVisionClient.isPublicHttpUrl). 리다이렉트마다 다시 묻는다
     */
    public WebPageFetcher(int connectTimeoutMillis, int readTimeoutMillis, int maxBytes, String userAgent,
                          Predicate<URI> allowed) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxBytes = maxBytes;
        this.userAgent = userAgent;
        this.allowed = allowed;
    }

    /**
     * HTML 페이지를 받는다. 못 받았거나(허용 안 된 주소·오류 응답·시간 초과) HTML 이 아니면 null.
     *
     * @param deadlineNanos System.nanoTime() 기준 마감. 넘으면 연결도 읽기도 하지 않는다
     */
    public Page fetchHtml(String url, long deadlineNanos) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (System.nanoTime() > deadlineNanos || !allowed.test(uri)) {
                return null;
            }
            HttpURLConnection conn = null;
            try {
                URLConnection raw = uri.toURL().openConnection();
                if (!(raw instanceof HttpURLConnection http)) {
                    return null;
                }
                conn = http;
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(connectTimeoutMillis);
                conn.setReadTimeout(readTimeoutMillis);
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9");

                int status = conn.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        return null;
                    }
                    uri = uri.resolve(location.trim());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    return null;
                }
                String contentType = conn.getContentType();
                if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                    return null;
                }
                try (InputStream in = conn.getInputStream()) {
                    byte[] body = readLimited(in, deadlineNanos);
                    return body.length == 0 ? null : new Page(body, contentType);
                }
            } catch (IOException | RuntimeException e) {
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null; // 리다이렉트가 너무 많다
    }

    private byte[] readLimited(InputStream in, long deadlineNanos) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int total = 0;
        while (total < maxBytes && System.nanoTime() <= deadlineNanos) {
            int n = in.read(buf, 0, Math.min(buf.length, maxBytes - total));
            if (n < 0) {
                break;
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }
}
