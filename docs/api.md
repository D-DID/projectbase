# UnderFaker API 명세 (10/30 코드 기준)

노션 '패키징 — API 명세서' 항목. 컨트롤러 4개 · 엔드포인트 19개를 코드에서 그대로 옮겼다.
**Swagger UI(10/31)**: 서버를 띄우고 http://localhost:8080/swagger-ui.html — [Authorize]에 로그인 토큰을 넣으면 바로 호출해 볼 수 있다(JSON: `/v3/api-docs`).

- Base URL: `http://localhost:8080`
- 공통 응답: `{"success": boolean, "data": T, "code": string, "message": string}` — 성공이면 code·message 가 null, 실패면 data 가 null
- 인증: `/api/auth/**` 와 `GET /api/recalls/**` 만 로그인 없이 된다. 나머지는 `Authorization: Bearer {accessToken}`. `/api/admin/**` 은 ROLE_ADMIN
- 크롬 확장(`chrome-extension://lnckaoecppbgieahhnioodeomjagomek`)은 CORS 허용 목록에 있다

## 1. 인증 — `AuthController` (`/api/auth`)

| 메서드 | 경로 | 본문 | 응답 data |
|---|---|---|---|
| POST | `/api/auth/signup` | `{email, password, username}` | 없음 |
| POST | `/api/auth/login` | `{email, password}` | `{accessToken, tokenType:"Bearer", expiresIn}` (기본 1시간) |

회원가입은 항상 ROLE_USER. 관리자는 DB 에서 `UPDATE user SET user_role='ADMIN' WHERE email=...`.

## 2. 검증 — `VerificationController` (`/api/verifications`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/manual` | 품명으로 제품 확인(웹 ① 직접 입력). 본문 `ManualInputRequest` |
| POST | `/manual/batch` | 크롬 확장이 쿠팡 구매이력을 묶어 보낼 때. 본문 `ManualInputRequest[]` |
| GET | `/{verificationId}` | 검증 결과 다시 보기 |
| POST | `/{verificationId}/image-check` | 2차 '사진으로 찾기'(Google Vision). 1차로 판단할 수 없을 때만 버튼이 열린다 |
| POST | `/image` | **사진 업로드 검증(FR-004, 10/31)** — multipart `file` 한 장(JPG·PNG·GIF·WEBP, 8MB 이하). OCR 없이 Vision 웹 검색으로 같은 사진이 실린 문서에서 반복된 상품명·KC 번호·모델명을 찾아 대조. 최대 '의심', 못 찾으면 '확인불가'. Vision 키가 없으면 V006 |
| GET | `/{verificationId}/evidence` | 판정 근거표(항목별 입력값·공표문값·점수·가중치) |
| GET | `/me?page=0&size=20&channel=WEB\|EXTENSION` | 내 검증 이력 — **마지막으로 확인한 순서**(10/31, `checkedAt`) |
| POST | `/url` | **URL 검증(FR-003·005, 10/31)** — 11/1 부터 웹 화면은 쓰지 않는다(우클릭 `/page` 로 바뀜, API 는 남김). 본문 `{url}`. 서버가 상품 페이지를 받아 JSON-LD·메타 태그·상품정보 고시 표에서 제품명·모델명·제조사·바코드·KC 인증번호·사진을 뽑고 판정. 쿠팡(서버 접속 차단)은 V003 — 웹은 쿠팡 주소를 크롬 확장으로 읽는다. 내부망 주소는 C001, 식별 정보를 못 찾으면 V004 |
| POST | `/url/preview` | URL 미리 읽기(10/31) — `/url` 과 같은 추출만 하고 검증은 저장하지 않는다. 응답 `{url, site, productName, brandName, modelName, makerName, barcodeNum, certNum, thumbnailUrl, kcStatus, kcText}`. 11/1 부터 웹 화면은 쓰지 않는다(API 는 남김) |
| POST | `/page` | **우클릭 검증(11/1, 확장 0.7.0)** — 본문 `{url, html?, fields?}`. 쇼핑몰 상세페이지·리콜 공표문에서 우클릭 'UnderFaker로 리콜 검증'을 누르면 확장이 그 탭의 주소·HTML(150만 자까지)과, 읽을 수 있으면 칸 값(`fields` = `ManualInputRequest` — 쿠팡은 상세페이지 고시 표, 공표문은 공표문 규칙)을 보낸다. 서버는 `fields` 를 먼저 쓰고 빈 칸만 HTML(JSON-LD·메타 태그·고시 표)에서 채워 판정. **서버가 페이지를 다시 받지 않는다**(쿠팡처럼 서버 접속을 막는 쇼핑몰도 된다). 기록은 `input_type=URL`, `channel=WEB`. 주소가 http(s) 가 아니면 C001, 식별 정보를 못 찾으면 V004(확장은 화면 글로 한 번 더 보낸다) |

### ManualInputRequest (전부 선택 입력)

| 필드 | 최대 길이 | 설명 |
|---|---|---|
| productName | 255 | 제품명(판매글 상품명 그대로 넣어도 된다 — 수식어는 서버가 정제) |
| brandName | 255 | 브랜드 |
| modelName | 255 | 모델명·품번 |
| makerName | 255 | 제조사·수입사 |
| barcodeNum | 64 | 바코드 |
| certNum | 64 | KC 인증번호. 설명이 섞여도 번호만 뽑아 쓴다(10/30) |
| thumbnailUrl | 500 | 상품 사진 주소(http/https). 2차 사진 찾기용 |
| kcStatus | 20 | 확장이 판단한 KC 칸 상태: `DISCLOSED`(번호 있음) · `REFERENCED`(참조·번호 없음) · `NONE`(KC 대상 아님) · `UNREADABLE` |
| kcText | 255 | KC 칸 원문. 번호가 여러 개면 서버가 전부 뽑아 조회·대조한다 |

### VerificationResultResponse 주요 필드

| 필드 | 설명 |
|---|---|
| finalResult | DB 저장 판정: `MATCH` · `PARTIAL` · `NO_MATCH` · `UNKNOWN` |
| **resultState** | 화면 표시 상태: `MATCH`(일치) · `PARTIAL`(의심) · `MISSING`(KC 번호 미표기) · `NO_MATCH`(불일치) · `UNKNOWN`(확인불가) · `EXCLUDED`(판단 제외 — (어린이제품이 아닌) 해외직구·구매대행) |
| imageCheckAvailable | '사진으로 찾기' 버튼을 보일지 — KC 번호를 못 얻었거나 KC 인증 DB 에 없는 번호일 때만 |
| imageCheck | 사진 찾기 결과 `FOUND` · `NONE` · `FAILED`(다시 누를 수 있음) |
| missingReason | 미표기·판단 제외 사유 문구 |
| imageProductName · imageCertNum · imageModelName | 사진 찾기로 2곳 이상 사이트에서 반복된 상품명·KC 번호·모델명 |
| kc | KC 인증 DB 조회 결과 `{status, certNum, certState, needsAttention, certDate, productName, modelName, makerName, makerCntryName, source, others}`. KC 칸에 번호가 여러 개면(예: 리뉴얼 전·후) 번호마다(최대 3개) 조회하고, 대표(첫 번째로 DB 에 있는 번호 — 판정의 '인증 모델명'에 씀) 밖 번호는 `others: [{certNum, status, certState, needsAttention, certDate, modelName}]` (10/31) |
| similarityScore · recallUid · recall* | 가장 잘 맞는 공표문과 종합 점수 |
| **input** | 저장된 입력값 `{productName, brandName, modelName, makerName, barcodeNum, certNum, thumbnailUrl, kcStatus, kcText}` — 리콜 후보가 없어도(불일치) 내려온다. 결과 화면 '내가 입력한 제품' (10/31) |
| recallCertNum | 매칭된 공표문의 KC 인증번호(콤마 구분 목록일 수 있음). 없으면 null (10/31) |
| channel | 검증이 들어온 경로 `WEB`(품명으로 제품 확인)·`EXTENSION`(쿠팡 구매이력). 결과 화면 '내 검증 이력 보기'가 같은 쪽 탭을 먼저 연다 (10/31) |

판정 규칙(요약, 10/31 2단계): **일치** = KC 인증번호 완전일치 + 제품명 유사도 50% 이상(`matching.rule.name-match`), 또는 품번(모델명)·인증 모델명 완전일치. **의심** = KC 번호만 같고 제품명이 다름 · 품번은 같은데 KC 번호가 다름 · 제품명 90% 이상(`matching.rule.name-suspect-other-cert`)인데 KC 번호가 다름 · 종합 점수가 partial 임계값 이상. 사진 찾기(2차) 근거는 최대 **의심**. 의심이 된 이유는 `/evidence` 의 `reason` 끝에 붙는다.

**공표문 원문 링크**: `https://www.safetykorea.kr/recall/ajax/recallBoard?recallUid={recallUid}` — `recallUid` 는 공공데이터 API 의 recallUid 그대로다. 웹 결과 화면은 일치·의심일 때 이 링크를 보여 준다.

**같은 입력 다시 확인(10/31)**: 같은 사용자·채널(WEB/EXTENSION)에서 제품명·브랜드·모델명·제조사·바코드·KC 인증번호·KC 칸 상태가 모두 같은 입력이면 새 검증을 만들지 않고 기존 `verificationId` 를 돌려준다. 그 사이 리콜 DB 가 갱신됐으면(recall.synced_at) 다시 판정한다. URL·사진 업로드 검증은 묶지 않는다.

**판정 근거 스냅샷(10/31)**: `/evidence` 의 항목별 대조표는 판정할 때 남긴 값을 그대로 보여 준다(매칭 규칙이 바뀌어도 과거 근거가 바뀌지 않음). 10/31 이전 검증은 예전처럼 다시 계산한다.

## 3. 리콜 조회 — `RecallController` (`/api/recalls`, 로그인 불필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/recalls?productName=&modelName=&makerName=&certNum=&page=&size=` | 적재된 리콜 검색(정규화 컬럼 LIKE). 조건이 비면 전체 |
| GET | `/api/recalls/{recallUid}` | 리콜 상세 + 사진 주소 목록 |

## 4. 관리자 동기화 — `AdminSyncController` (`/api/admin/sync`, ROLE_ADMIN)

| 메서드 | 경로 | 주요 파라미터 | 설명 |
|---|---|---|---|
| POST | `/recalls` | adminId | 설정된 공표일 하루 적재 |
| POST | `/recalls/range` | adminId, from, to(yyyyMMdd, 최대 400일), withImages, max, categoryFilter | 공표일 범위 적재(2023-07-12 이후만 조회됨) |
| POST | `/recalls/keywords` | adminId, keywords, expand, fromDb, prune, maxCalls, withImages | 품목명으로 훑어 과거분(2012~) 적재 |
| POST | `/recalls/images` | limit | 사진 없는 리콜의 사진만 채우기 |
| POST | `/certifications` | adminId, certNums, max | KC 인증 정보 적재 |
| GET | `/logs` | page, size | 동기화 이력 |

절차는 `db/README.md`, Postman 은 `db/recall-backfill.postman_collection.json`.

**자동 갱신(10/31, `RecallAutoSyncScheduler`)**: 위 API 를 부르지 않아도 서버가 뜰 때 한 번(20초 뒤)과 6시간마다(00:20·06:20·12:20·18:20, Asia/Seoul) 최근 7일 공표분을 `/recalls/range` 와 같은 코드로 다시 받고(본문만, upsert), 사진 없는 리콜 30건의 사진을 채운다. DB 마지막 공표일이 7일보다 오래됐으면 그날부터 받는다(최대 90일 — 더 오래된 빈틈은 `/recalls/range`). 이력은 ADMIN 계정(가장 작은 id)으로 `/logs` 에 남고, 관리자 계정이 없으면 서버 로그(`[RecallAutoSync]`)에만 남는다. 설정은 `recall.auto-sync.*`(`application.properties.example`), 끄려면 `recall.auto-sync.enabled=false`.

## 오류 코드

| 코드 | HTTP | 뜻 |
|---|---|---|
| C001 | 400 | 입력값 오류(본문은 raw JSON 으로) |
| C002 | 500 | 서버 오류 |
| C003 | 405 | 지원하지 않는 메서드 |
| C004 | 404 | 없는 경로 |
| C005 | 501 | 미구현(현재 쓰는 곳 없음 — `/url` 은 10/31 구현) |
| A001 | 409 | 이메일 중복 |
| A002 | 401 | 로그인 실패 |
| A003 | 401 | 토큰 무효·만료 |
| A004 | 401 | 로그인 필요 |
| A005 | 403 | 권한 없음 |
| V001 | 404 | 검증 요청 없음 |
| V002 | 400 | 지원하지 않는 이미지(형식·8MB 초과·빈 파일) |
| V003 | 502 | 상품 페이지를 못 불러옴(쿠팡 등 서버 접속 차단·시간 초과) |
| V004 | 200 | 추출 실패 — 직접 입력 안내 |
| V005 | 400 | 사진 없음 — 사진으로 찾기 불가 |
| V006 | 503 | 사진 판독 사용 불가(키 없음·월 상한 800건 초과 등) |
| E001 | 502 | 공공데이터 API 호출 실패 |
| E002 | 502 | (OCR 삭제됨 — 코드만 남음) |
| E003 | 404 | 리콜 정보 없음 |
