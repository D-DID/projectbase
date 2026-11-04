package com.underfaker.recallcheck.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 10/31 — API 문서(Swagger UI, springdoc). 노션 '패키징 — API 명세서: Swagger 또는 Postman' 항목.
 *
 * http://localhost:8080/swagger-ui.html 에서 엔드포인트를 보고 바로 호출해 볼 수 있다.
 * 1) /api/auth/login 으로 accessToken 을 받고 2) 오른쪽 위 [Authorize] 에 토큰만 넣으면(Bearer 는 자동)
 * 나머지 API 가 그 토큰으로 호출된다. 사람이 읽는 설명은 docs/api.md.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI underfakerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UnderFaker 리콜 확인 API")
                        .version("2026-10-31")
                        .description("구매한 유아·어린이 제품을 국가기술표준원 리콜 공표문(4,092건)과 대조한다. "
                                + "판정: 일치(KC 인증번호·품번 완전일치) · 의심 · KC 번호 미표기 · 불일치 · 판단 제외. "
                                + "공통 응답 {success, data, code, message}. 설명은 docs/api.md"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
