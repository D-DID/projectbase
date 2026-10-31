package com.underfaker.recallcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스프링 부트 진입점. 10/31 — @EnableScheduling: 리콜 데이터 자동 갱신(RecallAutoSyncScheduler) */
@EnableAsync
@EnableScheduling
@EnableJpaAuditing
@ConfigurationPropertiesScan
@SpringBootApplication
public class RecallCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallCheckApplication.class, args);
    }
}
