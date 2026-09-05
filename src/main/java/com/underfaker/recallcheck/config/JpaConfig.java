package com.underfaker.recallcheck.config;

import org.springframework.context.annotation.Configuration;

/**
 * JPA 관련 설정.
 * Auditing 활성화는 RecallCheckApplication 의 @EnableJpaAuditing 이 담당한다.
 * TODO 페이징 기본값(PageableHandlerMethodArgumentResolver) 필요 시 여기에 추가.
 */
@Configuration
public class JpaConfig {
}
