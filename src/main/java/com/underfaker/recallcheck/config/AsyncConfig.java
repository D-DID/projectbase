package com.underfaker.recallcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 스레드풀. 현재 검증 흐름은 동기로 동작하며,
 * verification.status(PENDING/DONE/FAILED) 기반 비동기 전환 시 사용한다.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "verificationExecutor")
    public Executor verificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("verify-");
        executor.initialize();
        return executor;
    }
}
