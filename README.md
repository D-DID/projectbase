# UnderFaker — 구매한 유아·어린이 제품 리콜 확인

쿠팡 구매이력(크롬 확장)이나 직접 입력한 제품 정보를 국가기술표준원 리콜 공표문 4,092건(2012-03 ~ 2026-09)과 대조해
**일치 · 의심 · KC 번호 미표기 · 불일치 · 판단 제외** 중 하나로 보여 준다. 판정은 단정하지 않고 근거표를 같이 보여 준다.

## 판단 흐름 (10/10 최종)

1. **범위** — 국내에서 파는 공산품·어린이제품(KC 인증 필수). (어린이제품이 아닌) 해외직구·구매대행은 판단 제외.
   어린이제품은 구매대행도 KC 표시가 없으면 금지라(어린이제품 안전 특별법 제30조) 판단한다.
2. **1차 — KC 인증번호 + 제품명** 으로 리콜 공표문 대조(10/31 2단계 판정).
   - **일치** = KC 인증번호 완전일치 + 제품명 유사도 50% 이상, 또는 품번(모델명)·인증 모델명 완전일치
   - **의심** = KC 번호만 같고 제품명이 다름 · 품번은 같은데 KC 번호가 다름 · 제품명이 거의 같은데(90%+) KC 번호가 다름 · 종합 점수 60%(유아·잡화 55%) 이상
   - 일치·의심이면 결과 화면에 **제품안전정보센터 공표문 원문 링크**가 나온다 — 사용자가 직접 확인
3. **2차 — 사진으로 찾기**(사용자가 누를 때만). KC 번호를 못 얻었거나 KC 인증 DB 에 없는 번호면 버튼이 열린다.
   Google Vision 이 같은 사진이 실린 웹 문서를 찾고, 2곳 이상 사이트에서 반복된 KC 번호·제품명·모델명으로 다시 대조. 결과는 최대 '의심'.

## 구성

| 폴더 | 내용 |
|---|---|
| `src/main/java/com/underfaker/recallcheck` | Spring Boot 4 서버 — controller · service(extraction / matching / sync / kc) · repository(JPA, 테이블 8개) |
| `src/main/resources/static` | 웹 화면(`index.html`, `js/app.js`) — 품명으로 제품 확인(① 직접 입력 ② 상품 URL ③ 쇼핑몰 화면 드래그 ④ 제품 사진 올리기) · 쿠팡 구매이력 · 이력 |
| `db/` | 리콜 덤프(4,092건)·적재 스크립트·Postman 컬렉션 |
| `docs/` | `api.md`(API 명세 — Swagger UI 는 서버 실행 후 `/swagger-ui.html`) · `edge-cases.md`(통합테스트 엣지케이스 20개) |
| 크롬 확장 | 별도 폴더(`underfaker-extension`, 0.6.2) — 쿠팡 주문목록 읽기(유아·어린이 제품만), 상세페이지 KC 칸 읽기, 우클릭 영역 드래그(쇼핑몰·제품안전정보센터 리콜 공표문) |

## 실행

자세한 순서는 [SETUP.md](SETUP.md). 요약:

1. MySQL 에 `recallcheck` 스키마 → `src/main/resources/schema.sql` 실행 → `db/load-recall.bat` 로 리콜 4,092건 적재
2. `src/main/resources/application.properties.example` 를 복사해 `application.properties` 를 만들고 DB 비밀번호·JWT 시크릿·SafetyKorea 인증키·(선택) Google Vision 키를 넣는다
   - **키·비밀번호는 application.properties 에만.** git·채팅·Notion 에 올리지 않는다(이 파일은 .gitignore 대상)
3. `gradlew bootRun` → http://localhost:8080
4. 크롬 `chrome://extensions` → 개발자 모드 → '압축해제된 확장 프로그램 로드' → `underfaker-extension` 폴더

## 테스트

- 서버: `gradlew test` — 단위·매칭 테스트(엣지케이스 20개 포함) + 통합 테스트(`ApiIntegrationTest` — 메모리 DB(H2)로 가입·로그인·검증·URL·사진 업로드·Swagger 까지 HTTP 로 확인, MySQL·키 불필요). `RecallCheckApplicationTests` 는 MySQL 이 떠 있어야 한다
- 확장: `node test/*.test.js` (Playwright 필요한 것은 `content.browser`·`capture.e2e`)

## 시연

[DEMO.md](DEMO.md) — 시연 순서와 입력값(실제 리콜 DB 로 결과를 미리 확인한 값).

## 한계 (발표 때 그대로 말할 것)

- 판정은 리콜 공표문과의 **대조 결과**다. '불일치'는 안전하다는 뜻이 아니다.
- 실측 라벨링 데이터셋이 없어 임계값은 잠정값이다(합성 회귀 점검만 했다 — `docs/edge-cases.md`).
- 유아제품 실측은 10/30 4건(구매 후 취소로 확보)뿐이다. 그때 나온 버그를 고친 0.6.1(유아·어린이 제품만 읽기, 헝겊책 KC 번호, 큰 사진으로 찾기)은 재시험 전이다.
- 주문목록에서 유아·어린이 제품은 **상품 제목**으로 고른다. 제목에 아기·유아·어린이·장난감 같은 단어가 없으면 빠질 수 있다.
- KC 번호가 상세 이미지 안에 그림으로만 있으면 읽지 못한다(OCR 은 10/1 스코프 컷).
- URL 검증(`/api/verifications/url`, 10/31)은 서버가 페이지를 직접 받는다 — 쿠팡처럼 서버 접속을 막는 쇼핑몰은 못 읽는다(쿠팡은 크롬 확장이 읽음). JSON-LD·고시 표가 없는 페이지는 제품명만 나올 수 있다.
- 사진 업로드 검증(10/31)은 OCR 없이 Google Vision 웹 검색만 쓴다 — 같은 사진이 실린 다른 사이트가 없으면 '확인불가'. 결과는 최대 '의심'.
- 같은 입력을 다시 확인하면 기존 검증을 다시 쓴다(10/31). 그 전에 쌓인 중복은 `db/dedupe-verifications.sql` 로 정리한다.
- 리콜 데이터 자동 갱신(10/31)은 **서버가 켜져 있을 때만** 돈다(기동 때 1회 + 6시간마다, 최근 7일·빈틈은 최대 90일). 공표일 조회는 2023-07-12 이후만 되므로 과거분은 덤프·키워드 적재로 넣는다.
