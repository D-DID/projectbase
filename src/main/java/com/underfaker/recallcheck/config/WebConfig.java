package com.underfaker.recallcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 설정.
 *
 * CORS 는 여기가 아니라 SecurityConfig 의 CorsConfigurationSource 빈에서 처리한다.
 * 시큐리티 필터가 MVC 보다 앞에서 돌기 때문에, 여기에만 설정하면 인증 실패 응답에는
 * CORS 헤더가 붙지 않아 프론트에서 원인을 알 수 없는 에러로 보인다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
