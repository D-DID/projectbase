package com.meta.projectbase.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration // 스프링부트 설정 빈으로 등록
@EnableWebSecurity // 스프링시큐리티 활성화
@RequiredArgsConstructor
public class SecurityConfig {
    private final PasswordEncoder passwordEncoder;
// Authentication Manager 빈 등록
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
    // 스프링시큐리티의 필터체인을 빈으로 등록, 규칙 정의
    // 참고 공식 문서 https://docs.spring.io/spring-security/reference/6.5/servlet/architecture.html#servlet-se
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF(Cross-Site Request Forgery) 교차 요청 위조 공격
                // JWT 토큰 기반 인증 방식은 CSRF를 방어하는 수단이므로 불필요함
                .csrf(AbstractHttpConfigurer::disable)
                // 토큰 기반 인증방식에서 불필요함
                .httpBasic(AbstractHttpConfigurer::disable)
                // 스프링 시큐리티가 제공하는 기본 로그인 폼을 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                // JWT 토큰 기반 인증 시스템에서는 세션 상태를 직접 저장하지 않는 방식이므로 = STATELESS
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인가(Authorization) 부분으로 엔드포인트 접근 권한을 설정하는 부분S
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("api/auth/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
}