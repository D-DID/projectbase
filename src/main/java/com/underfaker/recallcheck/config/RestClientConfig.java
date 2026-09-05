package com.underfaker.recallcheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
     * 국가기술표준원 Open API 전용 클라이언트.
     * 인증키는 쿼리 파라미터가 아니라 HTTP 헤더 AuthKey 로 보낸다(대소문자 구분).
     */
    @Bean
    public RestClient safetyKoreaRestClient() {
        return RestClient.builder()
                .baseUrl(safetyKoreaBaseUrl)
                .defaultHeader("AuthKey", safetyKoreaAuthKey)
                .build();
    }

    /** Python OCR 서버 전용 클라이언트 */
    @Bean
    public RestClient ocrRestClient() {
        return RestClient.builder()
                .baseUrl(ocrBaseUrl)
                .build();
    }
}
