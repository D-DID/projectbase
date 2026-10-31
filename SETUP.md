# 팀원 세팅 절차 (10/30 기준)

9/7 스켈레톤 적용 안내는 더 이상 쓰지 않는다(옛 내용은 git 이력에 있다). 처음 받는 사람은 아래 순서대로.

## 0. 필요한 것

- JDK 17 이상, MySQL 8, 크롬
- (선택) Google Cloud Vision API 키 — 없으면 '사진으로 찾기'만 V006 으로 막히고 나머지는 된다
- (선택) SafetyKorea Open API 인증키 — 덤프로 적재하면 리콜 조회에는 없어도 된다. KC 인증 DB 조회·새 리콜 동기화에 필요

## 1. DB

1. MySQL 에서 `CREATE DATABASE recallcheck DEFAULT CHARACTER SET utf8mb4;`
2. `src/main/resources/schema.sql` 실행
3. `db/load-recall.bat` 더블클릭 (MySQL 경로가 다르면 스크립트 맨 위 한 줄 수정)
4. 확인: `SELECT COUNT(*) FROM recallcheck.recall;` → **4092**. 자세한 확인 쿼리는 `db/README.md`

## 2. 설정 파일

1. `src/main/resources/application.properties.example` → 같은 폴더에 `application.properties` 로 복사
2. 채울 값
   - `spring.datasource.password` — 내 MySQL 비밀번호
   - `jwt.secret.key` — Base64 32바이트 이상(팀 공용 값은 개인 카톡으로 받는다)
   - `openapi.safety-korea.auth-key` — SafetyKorea 인증키(개인 카톡으로)
   - `google.vision.api-key` 또는 환경변수 `GOOGLE_VISION_API_KEY` — 선택
3. **이 파일의 값은 git·채팅방·Notion·`.example` 에 절대 쓰지 않는다.** `.example` 에는 자리표시만.

## 3. 서버

```
gradlew bootRun
```

http://localhost:8080 → 회원가입 → 로그인. 관리자 기능이 필요하면 DB 에서
`UPDATE user SET user_role = 'ADMIN' WHERE email = '내이메일';`

## 4. 크롬 확장 (0.6.1)

1. `chrome://extensions` → 오른쪽 위 **개발자 모드** 켜기
2. **압축해제된 확장 프로그램을 로드합니다** → `underfaker-extension` 폴더
3. 웹 화면(localhost:8080)을 **새로고침**(F5) — 화면에 '확장 프로그램 연결됨 (v0.6.1)'이 보이면 성공
4. 확장 폴더를 바꾼 뒤에는 `chrome://extensions` 에서 ⟳ → 웹 화면도 F5

확장이 하는 일:
- 쿠팡 구매이력 화면의 **최근 N건(10/30)** · **구매일자** 버튼 → 최소화된 작업 창에서 주문목록을 읽고, **유아·어린이 제품만** 골라 상세페이지 KC 칸을 읽어 서버로 보낸다(식품·생활용품은 건너뜀, 0.6.1)
- 품명 확인 ② **쿠팡 URL 붙여넣기** → 그 상품 페이지를 읽어 칸을 채운다
- 품명 확인 ③ **쇼핑몰 화면 우클릭 → UnderFaker → 영역 드래그** → 상품정보 표·사진을 가져와 칸을 채운다

## 5. 테스트

```
gradlew test
```

- 10/30 에 추가한 테스트 3개는 폴더가 깊어 `Claude outputs\1030\` 에 있다 → `copy-tests.cmd` 를 먼저 한 번 실행
- `RecallCheckApplicationTests` 는 DB·설정이 있어야 통과한다
- 매칭 엣지케이스 20개: `gradlew test --tests "*EdgeCaseScenariosTest"` (표는 `docs/edge-cases.md`)
- 통합 테스트(10/31): `gradlew test --tests "*ApiIntegrationTest"` — 메모리 DB(H2)·무작위 JWT 키로 돈다. MySQL·인증키 없이 된다
- API 문서(10/31): 서버 실행 후 http://localhost:8080/swagger-ui.html → `/api/auth/login` 으로 토큰을 받아 [Authorize]에 넣고 호출

## 자주 막히는 것

| 증상 | 원인·해결 |
|---|---|
| 기동 시 `Unknown database` / 스키마 검증 실패 | 1단계 schema.sql 안 돌림. `ddl-auto=validate` 라 스키마가 없으면 바로 멈춘다 |
| 모든 검증이 '대조할 리콜 후보가 없습니다' | 리콜 적재 안 됨 → `db/load-recall.bat` |
| 한글이 ??? 로 적재됨 | `--default-character-set=utf8mb4` 빠짐(배치 파일은 넣어 둠) |
| 사진으로 찾기 V006 | Vision 키 없음 또는 월 상한(800건) 초과 |
| 웹 화면에 '확장 프로그램 미감지' | 확장 로드 후 웹 화면 새로고침 안 함 |
| 확장 콘솔 'Extension context invalidated' | 확장을 새로고침한 뒤 열려 있던 페이지 — 그 페이지 F5 |
| 로그인·회원가입 400(C001) | Postman 본문을 raw → JSON 으로 |
| 사진 올리기가 500 또는 '너무 큽니다' | `application.properties` 의 `spring.servlet.multipart.max-file-size`(기본 1MB) — `.example` 처럼 10MB 로. 사진은 `app.upload.dir`(기본 ./uploads, git 제외)에 저장된다 |
| 같은 제품이 이력에 여러 번 보임 | 10/31 이전에 쌓인 중복 — `db/dedupe-verifications.sql`(미리보기 → 건수 확인 → 삭제 순서) |
| 구매이력 '읽는 도중 오류', 확장 로그 줄 번호가 예전과 같음 | 서비스워커가 옛 코드로 돌고 있음 → `chrome://extensions` ⟳ → 웹 화면 F5 |
| 작업 창이 화면에 남거나 탭이 안 닫힘 | `background.js` 위쪽 `HIDDEN_WORK_WINDOW = false` → 예전처럼 현재 창의 비활성 탭으로 읽는다 |
| 사진으로 찾기 '같은 사진 없음' | 사진이 작거나 같은 사진이 실린 웹 문서가 없음. 0.6.1 부터 상세 대표사진·큰 사진(492px)으로 먼저 찾는다 |
