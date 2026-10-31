package com.underfaker.recallcheck.integration;

import com.jayway.jsonpath.JsonPath;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.repository.RecallRepository;
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

        Integer id = JsonPath.read(body, "$.data.verificationId");
        mvc.perform(get("/api/verifications/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input.certNum").value(CHESS_CERT));
        mvc.perform(get("/api/verifications/" + id + "/evidence").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comparisons[?(@.field == 'certNum')].score").value(1.0));
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
}
