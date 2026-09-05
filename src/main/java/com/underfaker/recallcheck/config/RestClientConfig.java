package com.underfaker.recallcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 외부 API 호출 클라이언트·타임아웃 */
@Configuration
public class RestClientConfig {

    /** 국가기술표준원 Open API 전용 클라이언트 */
    @Bean
    public RestClient safetyKoreaRestClient() {
        // TODO base-url 을 application.properties 의 openapi.safety-korea.base-url 에서 주입
        return RestClient.builder().build();
    }

    /** Python OCR 서버 전용 클라이언트 */
    @Bean
    public RestClient ocrRestClient() {
        // TODO base-url 을 application.properties 의 ocr.base-url 에서 주입
        return RestClient.builder().build();
    }

    @SuppressWarnings("unused")
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
}
