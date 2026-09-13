package com.underfaker.recallcheck.config;

import com.underfaker.recallcheck.security.CustomAccessDeniedHandler;
import com.underfaker.recallcheck.security.CustomAuthenticationEntryPoint;
import com.underfaker.recallcheck.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 인증·인가 정책, 필터 체인 등록.
 *
 * REST + JWT 구조라 세션을 만들지 않고(STATELESS), 폼 로그인과 HTTP Basic 을 끈다.
 * 이 빈이 없으면 스프링 부트 기본 보안이 적용되어 모든 요청이 /login 으로 리다이렉트된다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize 등 메서드 단위 권한 제어 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인증·인가 실패 응답을 ApiResponse 포맷으로 통일한다.
                // 시큐리티 필터는 DispatcherServlet 앞에서 동작해서
                // GlobalExceptionHandler 가 잡지 못하므로 여기서 따로 등록해야 한다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)  // 401
                        .accessDeniedHandler(customAccessDeniedHandler))           // 403
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 정적 리소스 — 데모 화면(index.html)
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/css/**", "/js/**", "/img/**", "/assets/**").permitAll()
                        // FR-001 회원가입, FR-002 로그인
                        .requestMatchers("/api/auth/**").permitAll()
                        // 공개 리콜 조회 — 로그인 없이도 확인할 수 있어야 한다
                        .requestMatchers(HttpMethod.GET, "/api/recalls/**").permitAll()
                        // 관리자 전용 (FR-016, FR-017)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정. 스프링 시큐리티가 MVC 보다 앞에서 동작하므로
     * WebMvcConfigurer 쪽이 아니라 여기에서 정의해야 실제로 적용된다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000", "http://localhost:5173", "http://localhost:8080",
                // UnderFaker 크롬 확장 — manifest.json의 key 필드로 ID 고정해둠(팀원 전원 동일 ID)
                "chrome-extension://lnckaoecppbgieahhnioodeomjagomek"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
