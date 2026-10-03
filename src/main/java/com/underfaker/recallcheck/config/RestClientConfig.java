package com.underfaker.recallcheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 외부 API 호출 클라이언트·타임아웃 */
@Configuration
public class RestClientConfig {

    @Value("${openapi.safety-korea.base-url}")
    private String safetyKoreaBaseUrl;

    @Value("${openapi.safety-korea.auth-key}")
    private String safetyKoreaAuthKey;

    @Value("${ocr.base-url}")
    private String ocrBaseUrl;

    /**
     * 국가기술표준원 Open API 전용 클라이언트 — 관리자 적재(리콜 목록·상세, KC 동기화)용.
     * 인증키는 쿼리 파라미터가 아니라 HTTP 헤더 AuthKey 로 보낸다(대소문자 구분).
     *
     * 10/3 — 타임아웃 추가. 이전엔 타임아웃이 없어서 API 가 응답을 안 주면 요청이 끝없이 멈췄다
     * (9/19 KC 동기화에서 실제로 멈춤). 목록 조회는 1,000건 넘게 돌려줄 때가 있어 읽기는 넉넉히 둔다.
     */
    @Bean
    public RestClient safetyKoreaRestClient() {
        return RestClient.builder()
                .baseUrl(safetyKoreaBaseUrl)
                .defaultHeader("AuthKey", safetyKoreaAuthKey)
                .requestFactory(timeouts(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .build();
    }

    /**
     * 10/3 추가 — 검증 중 KC 인증번호 1건 조회 전용(KcLookupService).
     * 사용자가 결과를 기다리는 중에 부르므로 짧게 끊는다. 실패해도 판정은 계속된다("KC 조회 불가" 표시).
     * 10/3 실측: 번호 1건 조회 응답은 1KB 안팎.
     */
    @Bean
    public RestClient safetyKoreaLookupRestClient() {
        return RestClient.builder()
                .baseUrl(safetyKoreaBaseUrl)
                .defaultHeader("AuthKey", safetyKoreaAuthKey)
                .requestFactory(timeouts(Duration.ofSeconds(3), Duration.ofSeconds(8)))
                .build();
    }

    /** Python OCR 서버 전용 클라이언트 */
    @Bean
    public RestClient ocrRestClient() {
        return RestClient.builder()
                .baseUrl(ocrBaseUrl)
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}
