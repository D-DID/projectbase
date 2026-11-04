package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.client.GoogleVisionClient;
import com.underfaker.recallcheck.client.WebPageFetcher;
import com.underfaker.recallcheck.common.ProductPageParser;
import com.underfaker.recallcheck.common.WebPageIdentifiers;
import com.underfaker.recallcheck.dto.internal.UrlExtraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * FR-005 — URL 페이지 텍스트 파싱(10/31 구현).
 *
 * 서버가 상품 페이지 HTML 을 받아(WebPageFetcher — 내부망 주소·리다이렉트마다 검사, 크기·시간 제한)
 * ProductPageParser 로 제품명·모델명·제조사·바코드·KC 인증번호·사진 주소를 뽑는다.
 *
 * 쿠팡은 서버 요청을 봇 차단(Akamai)으로 막는다(9/10 Postman 실측 403). 그래서 쿠팡 주소는 받으러 가지 않고
 * BLOCKED_SITE 로 돌려준다 — 웹 화면은 쿠팡 주소를 크롬 확장으로 읽는다(② 방식). 다른 쇼핑몰도 막히면
 * FETCH_FAILED 이고, 화면은 ③ 영역 드래그나 ① 직접 입력을 안내한다.
 * 예외를 던지지 않는다.
 */
@Slf4j
@Component
public class WebTextExtractor {

    /** 서버 접속을 막는 걸로 확인된 쇼핑몰(9/10 실측). 받으러 가지 않는다 */
    static final List<String> SERVER_BLOCKED_HOSTS = List.of("coupang.com");

    static final int CONNECT_TIMEOUT_MILLIS = 4_000;
    static final int READ_TIMEOUT_MILLIS = 6_000;
    static final long TOTAL_TIMEOUT_MILLIS = 12_000;
    static final int MAX_PAGE_BYTES = 3_000_000;

    /** 데스크톱 크롬과 같은 형태 — 일부 쇼핑몰은 알 수 없는 UA 에 빈 페이지를 준다 */
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final WebPageFetcher fetcher = new WebPageFetcher(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS,
            MAX_PAGE_BYTES, USER_AGENT, GoogleVisionClient::isPublicHttpUrl);

    /**
     * @param url 사용자가 입력한 상품 페이지 URL(http/https — UrlVerifyRequest 에서 검사)
     */
    public UrlExtraction read(String url) {
        String host = hostOf(url);
        if (host == null) {
            return new UrlExtraction(UrlExtraction.Status.NOT_ALLOWED, ProductPageParser.Parsed.EMPTY, null);
        }
        if (isServerBlocked(host)) {
            return new UrlExtraction(UrlExtraction.Status.BLOCKED_SITE, ProductPageParser.Parsed.EMPTY, host);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return new UrlExtraction(UrlExtraction.Status.NOT_ALLOWED, ProductPageParser.Parsed.EMPTY, host);
        }
        if (!GoogleVisionClient.isPublicHttpUrl(uri)) {
            return new UrlExtraction(UrlExtraction.Status.NOT_ALLOWED, ProductPageParser.Parsed.EMPTY, host);
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOTAL_TIMEOUT_MILLIS);
        WebPageFetcher.Page page = fetcher.fetchHtml(url, deadline);
        if (page == null) {
            log.info("[UrlExtract] 페이지를 받지 못함 — {}", host);
            return new UrlExtraction(UrlExtraction.Status.FETCH_FAILED, ProductPageParser.Parsed.EMPTY, host);
        }
        String html = WebPageIdentifiers.decode(page.body(), page.contentType());
        ProductPageParser.Parsed parsed = ProductPageParser.parse(html, url);
        log.info("[UrlExtract] {} — 제품명:{} 모델명:{} 제조사:{} KC:{}({}) 사진:{}", host,
                parsed.productName() != null, parsed.modelName() != null, parsed.makerName() != null,
                parsed.certNum(), parsed.kcStatus(), parsed.thumbnailUrl() != null);
        return new UrlExtraction(parsed.isEmpty() ? UrlExtraction.Status.EMPTY : UrlExtraction.Status.OK, parsed, host);
    }

    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            String h = new URI(url.trim()).getHost();
            return h == null || h.isBlank() ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isServerBlocked(String host) {
        for (String blocked : SERVER_BLOCKED_HOSTS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }
}
