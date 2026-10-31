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
| POST | `/manual` | 품명으로 제품 확인(웹 ①②③ 입력 방식 공통). 본문 `ManualInputRequest` |
| POST | `/manual/batch` | 크롬 확장이 쿠팡 구매이력을 묶어 보낼 때. 본문 `ManualInputRequest[]` |
| GET | `/{verificationId}` | 검증 결과 다시 보기 |
| POST | `/{verificationId}/image-check` | 2차 '사진으로 찾기'(Google Vision). 1차로 판단할 수 없을 때만 버튼이 열린다 |
| POST | `/image` | **사진 업로드 검증(FR-004, 10/31)** — multipart `file` 한 장(JPG·PNG·GIF·WEBP, 8MB 이하). OCR 없이 Vision 웹 검색으로 같은 사진이 실린 문서에서 반복된 상품명·KC 번호·모델명을 찾아 대조. 최대 '의심', 못 찾으면 '확인불가'. Vision 키가 없으면 V006 |
| GET | `/{verificationId}/evidence` | 판정 근거표(항목별 입력값·공표문값·점수·가중치) |
| GET | `/me?page=0&size=20&channel=WEB\|EXTENSION` | 내 검증 이력 — **마지막으로 확인한 순서**(10/31, `checkedAt`) |
| POST | `/url` | **URL 검증(FR-003·005, 10/31)** — 본문 `{url}`. 서버가 상품 페이지를 받아 JSON-LD·메타 태그·상품정보 고시 표에서 제품명·모델명·제조사·바코드·KC 인증번호·사진을 뽑고 판정. 쿠팡(서버 접속 차단)은 V003 — 웹은 쿠팡 주소를 크롬 확장으로 읽는다. 내부망 주소는 C001, 식별 정보를 못 찾으면 V004 |
| POST | `/url/preview` | URL 미리 읽기(10/31) — `/url` 과 같은 추출만 하고 검증은 저장하지 않는다. 응답 `{url, site, productName, brandName, modelName, makerName, barcodeNum, certNum, thumbnailUrl, kcStatus, kcText}`. 웹 ② 방식이 칸 채우기에 쓴다 |

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
| kcStatus | 20 | 확장·URL 읽기가 판단한 KC 칸 상태: `DISCLOSED`(번호 있음) · `REFERENCED`(참조·번호 없음) · `NONE`(KC 대상 아님) · `UNREADABLE` |
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
| kc | KC 인증 DB 조회 결과 `{status, certNum, certState, needsAttention, certDate, productName, modelName, makerName, makerCntryName, source}` |
| similarityScore · recallUid · recall* | 가장 잘 맞는 공표문과 종합 점수 |
| **input** | 저장된 입력값 `{productName, brandName, modelName, makerName, barcodeNum, certNum, thumbnailUrl, kcStatus, kcText}` — 리콜 후보가 없어도(불일치) 내려온다. 결과 화면 '내가 입력한 제품' (10/31) |
| recallCertNum | 매칭된 공표문의 KC 인증번호(콤마 구분 목록일 수 있음). 없으면 null (10/31) |

판정 규칙(요약): **일치**는 KC 인증번호·품번(모델명)·인증 모델명이 완전히 같을 때만(10/31 — 제품명 등 텍스트만 100%면 **의심**). 사진 찾기(2차) 근거는 최대 **의심**.

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
