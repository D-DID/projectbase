package com.underfaker.recallcheck.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 10/10 — 사진 찾기 페이지 읽기. 리다이렉트로 허용 안 된 주소에 가지 않는지, 크기 상한·HTML 여부를 본다.
 * 로컬 HTTP 서버(JDK 내장)로 확인한다. 허용 여부는 경로로 흉내 낸다(/internal 로 시작하면 내부망으로 본다).
 */
class WebPageFetcherTest {

    static HttpServer server() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/page", ex -> {
            byte[] b = "<p>KC 인증번호 CB063R423-5001</p>".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.createContext("/to-page", ex -> {
            ex.getResponseHeaders().add("Location", "/page");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        s.createContext("/to-internal", ex -> {
            ex.getResponseHeaders().add("Location", "/internal/meta-data");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        s.createContext("/internal", ex -> {
            byte[] b = "<p>secret</p>".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.createContext("/image", ex -> {
            byte[] b = new byte[]{1, 2, 3};
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
        return s;
    }

    static WebPageFetcher fetcher(int maxBytes) {
        return new WebPageFetcher(2_000, 2_000, maxBytes, "test",
                uri -> uri.getPath() == null || !uri.getPath().startsWith("/internal"));
    }

    static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    }

    @Test
    void 리다이렉트는_따라가되_허용_안_된_주소로는_가지_않는다() throws Exception {
        HttpServer s = server();
        try {
            String base = "http://127.0.0.1:" + s.getAddress().getPort();
            WebPageFetcher.Page page = fetcher(1_000_000).fetchHtml(base + "/to-page", deadline());
            assertEquals("<p>KC 인증번호 CB063R423-5001</p>", new String(page.body(), StandardCharsets.UTF_8));
            assertNull(fetcher(1_000_000).fetchHtml(base + "/to-internal", deadline()));
            assertNull(fetcher(1_000_000).fetchHtml(base + "/internal/x", deadline()));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void HTML_이_아니거나_마감이_지났으면_받지_않고_크기_상한까지만_읽는다() throws Exception {
        HttpServer s = server();
        try {
            String base = "http://127.0.0.1:" + s.getAddress().getPort();
            assertNull(fetcher(1_000_000).fetchHtml(base + "/image", deadline()));
            assertNull(fetcher(1_000_000).fetchHtml(base + "/page", System.nanoTime() - 1));
            assertEquals(5, fetcher(5).fetchHtml(base + "/page", deadline()).body().length);
        } finally {
            s.stop(0);
        }
    }
}
