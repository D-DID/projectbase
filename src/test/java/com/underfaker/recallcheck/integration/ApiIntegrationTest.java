package com.underfaker.recallcheck.integration;

import com.jayway.jsonpath.JsonPath;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.service.sync.RecallAutoSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 10/31 — 통합 테스트: 실제 스프링 컨텍스트(보안·컨트롤러·서비스·JPA)를 띄우고 HTTP 요청으로 흐름을 끝까지 확인한다.
 * 노션 '품질 — 통합 테스트' 항목.
 *
 * DB 는 메모리 H2(MySQL 호환 모드, src/test/resources/application-it.properties), 외부 API(SafetyKorea·Vision)는
 * 닿지 않게 둔다. 그래서 MySQL·키 없이 `gradlew test` 로 돈다. JWT 서명 키는 실행할 때마다 무작위로 만든다.
 */
@SpringBootTest
@ActiveProfiles("it")
class ApiIntegrationTest {

    private static final long CHESS_UID = 990_001L;
    private static final String CHESS_CERT = "CB067R227-2006";

    @Autowired
    WebApplicationContext context;

    @Autowired
    RecallRepository recallRepository;

    @Autowired
    RecallAutoSyncScheduler recallAutoSync;

    @Autowired
    ApiSyncLogRepository apiSyncLogRepository;

    MockMvc mvc;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) throws IOException {
        byte[] key = new byte[48];
        new SecureRandom().nextBytes(key);
        String jwtKey = Base64.getEncoder().encodeToString(key);
        Path tmp = Files.createTempDirectory("underfaker-it");
        registry.add("jwt.secret.key", () -> jwtKey);
        registry.add("app.upload.dir", () -> tmp.resolve("uploads").toString());
        registry.add("ocr.budget.state-file", () -> tmp.resolve("ocr-budget.properties").toString());
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        if (recallRepository.findById(CHESS_UID).isEmpty()) {
            recallRepository.save(Recall.builder()
                    .recallUid(CHESS_UID)
                    .recallProductName("테스트 원목 체스 세트")
                    .recallModelName("테스트 원목 체스 세트")
                    .certNum(CHESS_CERT)
                    .categoryName("완구")
                    .recallCmpnyName("바니랜드")
                    .publishDate("20260916")
                    .harmDscr("통합 테스트용 가짜 공표문")
                    .build());
        }
    }

    /** 새 사용자로 가입·로그인하고 accessToken 을 돌려준다(테스트끼리 이력이 섞이지 않게 매번 새 계정) */
    private String login() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        String password = "pw-" + UUID.randomUUID();
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"username\":\"테스트\"}"))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        assertNotNull(token);
        return token;
    }

    private String manual(String token, String json) throws Exception {
        return mvc.perform(post("/api/verifications/manual").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 로그인하지_않으면_검증_API를_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/verifications/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void KC_인증번호가_같으면_일치_결과에_입력값과_공표문_인증번호가_있다() throws Exception {
        String token = login();
        String body = manual(token, "{\"productName\":\"원목 체스\",\"certNum\":\"" + CHESS_CERT + "\"}");
        assertEquals("MATCH", JsonPath.read(body, "$.data.resultState"));
        assertEquals("원목 체스", JsonPath.read(body, "$.data.input.productName"));
        assertEquals(CHESS_CERT, JsonPath.read(body, "$.data.recallCertNum"));
        assertEquals("WEB", JsonPath.read(body, "$.data.channel"));   // 10/31 — '내 검증 이력 보기' 탭 선택용

        Integer id = JsonPath.read(body, "$.data.verificationId");
        mvc.perform(get("/api/verifications/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input.certNum").value(CHESS_CERT));
        mvc.perform(get("/api/verifications/" + id + "/evidence").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comparisons[?(@.field == 'certNum')].score").value(1.0));
    }

    @Test
    void KC_인증번호만_같고_제품명이_다르면_의심_근거에_이유가_남는다() throws Exception {
        String token = login();
        String body = manual(token, "{\"productName\":\"아기 물티슈 100매\",\"certNum\":\"" + CHESS_CERT + "\"}");
        assertEquals("PARTIAL", JsonPath.read(body, "$.data.resultState"));
        assertEquals(CHESS_UID, ((Number) JsonPath.read(body, "$.data.recallUid")).longValue());
        Integer id = JsonPath.read(body, "$.data.verificationId");
        String evidence = mvc.perform(get("/api/verifications/" + id + "/evidence").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reason = JsonPath.read(evidence, "$.data.reason");
        assertTrue(reason.contains("KC 인증번호는 같지만 제품명이 달라"), reason);
    }

    @Test
    void 제품명만_같으면_의심까지() throws Exception {
        String token = login();
        String body = manual(token, "{\"productName\":\"테스트 원목 체스 세트\"}");
        assertEquals("PARTIAL", JsonPath.read(body, "$.data.resultState"));
    }

    @Test
    void 같은_입력을_다시_보내면_새로_쌓지_않고_같은_검증을_돌려준다() throws Exception {
        String token = login();
        String json = "{\"productName\":\"유아 실리콘 턱받이\",\"makerName\":\"테스트상사\"}";
        Integer first = JsonPath.read(manual(token, json), "$.data.verificationId");
        Integer second = JsonPath.read(manual(token, json), "$.data.verificationId");
        assertEquals(first, second);
        mvc.perform(get("/api/verifications/me?channel=WEB").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 같은_입력에_사진_주소를_더하면_사진으로_찾기_버튼이_열린다() throws Exception {
        // 11/4 실측 — 사진 주소는 '같은 입력' 비교에서 빠져서, 사진 없이 확인한 뒤 사진 주소를 넣어 다시 확인해도
        // 예전 검증(사진 없음)을 그대로 돌려줬다. 화면 안내('사진 주소를 넣으면 다시 확인할 수 있습니다')와 어긋났다.
        String token = login();
        String first = manual(token, "{\"productName\":\"유아 실리콘 턱받이\"}");
        assertEquals("MISSING", JsonPath.read(first, "$.data.resultState"));
        assertEquals(Boolean.FALSE, JsonPath.read(first, "$.data.imageCheckAvailable"));
        String second = manual(token,
                "{\"productName\":\"유아 실리콘 턱받이\",\"thumbnailUrl\":\"https://example.com/bib.jpg\"}");
        assertEquals((Integer) JsonPath.read(first, "$.data.verificationId"),
                (Integer) JsonPath.read(second, "$.data.verificationId"));
        assertEquals(Boolean.TRUE, JsonPath.read(second, "$.data.imageCheckAvailable"));
    }

    @Test
    void 쿠팡_주소는_서버에서_읽지_않고_V003() throws Exception {
        String token = login();
        mvc.perform(post("/api/verifications/url").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.coupang.com/vp/products/123\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("V003"));
    }

    @Test
    void 내부망_주소는_열지_않는다() throws Exception {
        String token = login();
        mvc.perform(post("/api/verifications/url/preview").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:8080/admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 사진_업로드는_Vision_키가_없으면_V006() throws Exception {
        String token = login();
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        mvc.perform(multipart("/api/verifications/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    void 리콜_검색은_로그인_없이_된다() throws Exception {
        mvc.perform(get("/api/recalls").param("productName", "원목 체스"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void API_문서가_열린다() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    /**
     * 10/31 — 리콜 자동 갱신. 이 테스트에서 Open API 는 닿지 않는 주소다(application-it.properties).
     * 예외 없이 끝나고 못 받은 날을 돌려주며, 관리자 계정이 없으면 이력(api_sync_log) 행을 남기지 않는다.
     * 범위는 최근 7일 이상(DB 마지막 공표일 20260916 이 더 오래됐으면 그날부터, 최대 90일).
     */
    @Test
    void 리콜_자동_갱신은_API가_닿지_않아도_멈추지_않는다() {
        long logs = apiSyncLogRepository.count();
        RangeSyncResponse r = recallAutoSync.run("테스트");
        assertNotNull(r);
        assertTrue(r.daysScanned() >= 7 && r.daysScanned() <= 90, "훑은 날 " + r.daysScanned());
        assertEquals(r.daysScanned(), r.failedDates().size());
        assertEquals(logs, apiSyncLogRepository.count());
    }

    /** 11/1 — 쇼핑몰 상세페이지 우클릭 검증(확장 0.7.0). 확장이 보낸 HTML 에서 서버가 값을 뽑아 판정한다(페이지를 받으러 가지 않는다) */
    @Test
    void 우클릭_검증_HTML에서_제품명과_KC를_읽어_판정한다() throws Exception {
        String token = login();
        String html = "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"Product\",\"name\":\"원목 체스 세트\"}</script></head><body>"
                + "<table><tr><th>KC 인증정보</th><td>" + CHESS_CERT + "</td></tr></table></body></html>";
        String json = "{\"url\":\"https://shop.example.com/goods/1\",\"html\":" + quote(html) + "}";
        String body = mvc.perform(post("/api/verifications/page").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        assertEquals("MATCH", JsonPath.read(body, "$.data.resultState"));
        assertEquals("원목 체스 세트", JsonPath.read(body, "$.data.input.productName"));
        assertEquals(CHESS_CERT, JsonPath.read(body, "$.data.input.certNum"));
        assertEquals("WEB", JsonPath.read(body, "$.data.channel"));   // 품명 검증 이력에 남는다
    }

    /** 11/1 — 확장이 직접 읽은 값(쿠팡 고시·공표문)이 있으면 그 값을 먼저 쓴다. HTML 없이도 된다 */
    @Test
    void 우클릭_검증_확장이_읽은_값을_먼저_쓴다() throws Exception {
        String token = login();
        String json = "{\"url\":\"https://www.coupang.com/vp/products/1\",\"fields\":{\"productName\":\"원목 체스\","
                + "\"certNum\":\"" + CHESS_CERT + "\",\"kcStatus\":\"DISCLOSED\",\"kcText\":\"" + CHESS_CERT + "\"}}";
        String body = mvc.perform(post("/api/verifications/page").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("MATCH", JsonPath.read(body, "$.data.resultState"));
        assertEquals("원목 체스", JsonPath.read(body, "$.data.input.productName"));
    }

    /** 11/1 — 읽을 값이 하나도 없으면 V004(직접 입력 안내). 주소가 http(s) 가 아니면 C001 */
    @Test
    void 우클릭_검증_값이_없으면_V004_주소가_틀리면_C001() throws Exception {
        String token = login();
        mvc.perform(post("/api/verifications/page").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://shop.example.com/x\",\"html\":\"<html><body>안녕</body></html>\"}"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("V004"));
        mvc.perform(post("/api/verifications/page").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"chrome://settings\",\"html\":\"<html></html>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 10/31 — 화면 주소(/swagger-ui.html)도 로그인 없이 열린다(→ /swagger-ui/index.html 로 넘어간다) */
    @Test
    void Swagger_화면이_로그인_없이_열린다() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
