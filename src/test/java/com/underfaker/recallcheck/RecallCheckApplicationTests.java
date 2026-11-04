package com.underfaker.recallcheck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 application.properties(MySQL)로 컨텍스트가 뜨는지 본다.
 * 10/31 — 리콜 자동 갱신은 끈다. 켜 두면 테스트 중에 기동 갱신 스레드가 실제 Open API 를 부르고 DB 에 적재한다.
 */
@SpringBootTest(properties = "recall.auto-sync.enabled=false")
class RecallCheckApplicationTests {

    @Test
    void contextLoads() {
    }
}
