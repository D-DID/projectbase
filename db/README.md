# 리콜 데이터 공유

국가기술표준원 SafetyKorea Open API 에서 받은 리콜 데이터다.
각자 적재할 필요 없이 이 덤프를 넣으면 된다.

| 항목 | 값 |
|---|---|
| 리콜 건수 | 4,092 (게시판 4,249건의 약 96%) |
| 공표일 범위 | 2012-03-05 ~ 2026-09-16 |
| 포함 테이블 | `recall`, `recall_file` |
| 정규화 검증 | `normalized_cert_num` / `normalized_model_name` 빈 문자열 0건 |
| 기준일 | 2026-10-01 |

---

## 팀원: 적재하는 법

**1. 스키마를 먼저 만든다.**

`src/main/resources/schema.sql` 을 `recallcheck` 스키마에 실행한다.
이미 개발 중이라 테이블이 있으면 건너뛴다.

**2. `load-recall.bat` 을 더블클릭한다.**

MySQL 설치 경로가 `C:\Program Files\MySQL\MySQL Server 8.0\bin` 이 아니면
스크립트 맨 위 `MYSQL_BIN` 한 줄만 고친다.

배치 파일을 안 쓰겠다면 직접 실행해도 된다. **`--default-character-set=utf8mb4` 를 빠뜨리면 한글이 깨진다.**

```
mysql -u root -p --default-character-set=utf8mb4 recallcheck < recall-dump.sql
```

**3. 확인한다.**

```sql
SELECT COUNT(*) AS 총건수,
       COUNT(CASE WHEN normalized_cert_num  = '' THEN 1 END) AS 빈문자열_인증번호,
       COUNT(CASE WHEN normalized_model_name = '' THEN 1 END) AS 빈문자열_모델명,
       MIN(publish_date) AS 최초공표,
       MAX(publish_date) AS 최종공표
FROM recallcheck.recall;
```

`4092 / 0 / 0 / 20120305 / 20260916` 이 나와야 한다.

**빈 문자열 두 칸이 0 인지 꼭 볼 것.** 여기가 0 이 아니면 후보조회가
`LIKE '%%'` 로 번역돼서 리콜 전건이 후보로 끌려온다. 인증번호 없는 상품 하나가
리콜 전건과 매칭되는 사고가 여기서 난다.

---

## mysqldump 가 없을 때 — IntelliJ 로 뜨는 법

MySQL 을 서버만 설치하면 `mysqldump.exe` 가 안 깔려 있을 수 있다.
배치 파일이 `mysqldump.exe not found` 를 띄우면 이 경로를 쓴다. 외부 도구가 필요 없다.

**내보내기 (적재한 사람)**

1. IntelliJ 오른쪽 **Database** 패널에서 `recallcheck` 스키마를 편다
2. `recall` 과 `recall_file` 두 테이블을 **Ctrl 눌러 같이 선택**
3. 우클릭 → **Import/Export** → **Export Data to File...**
4. 왼쪽 포맷 목록에서 **SQL Inserts** 선택
5. 저장 경로를 `projectbase\db\recall-dump.sql` 로 지정하고 Export

**불러오기 (팀원)**

1. `schema.sql` 을 먼저 실행해 테이블을 만든다
2. 기존 데이터를 비운다 — INSERT 만 들어 있어서 안 비우면 PK 충돌이 난다

   ```sql
   DELETE FROM match_result;
   DELETE FROM recall_file;
   DELETE FROM recall;
   ```
3. `recall-dump.sql` 을 IntelliJ 콘솔에서 열고 전체 실행

**이 방식의 차이점.** `SQL Inserts` 는 `INSERT` 문만 만든다. `DROP TABLE` / `CREATE TABLE` 이
없으니 테이블이 미리 있어야 하고, 위처럼 직접 비워야 한다. 배치 파일 쪽(`mysqldump`)은
`--add-drop-table` 이 붙어 있어서 그 과정이 자동이다. 결과 데이터는 같다.

---

## 주의사항

**`user` 테이블은 안 들어 있다.** 비밀번호 해시가 담기는 테이블이라 제외했다.
각자 `POST /api/auth/signup` 으로 계정을 만들고, 관리자 기능(`/api/admin/**`)이
필요하면 직접 올린다.

```sql
UPDATE `user` SET user_role = 'ADMIN' WHERE email = '본인이메일';
```

**적재하다 `Cannot delete or update a parent row` 가 뜨면**
`match_result` 에 남은 테스트 기록이 `recall` 을 참조하고 있는 것이다.
`match_result.recall_uid` 에 `ON DELETE CASCADE` 가 없어서 그렇다. 먼저 비우고 다시 돌린다.

```sql
DELETE FROM match_result;
DELETE FROM extraction;
DELETE FROM verification;
```

**사진(`recall_file`)은 비어 있다.** 본문만 `withImages=false` 로 적재했다.
사진이 필요하면 적재한 사람이 `POST /api/admin/sync/recalls/images` 를 돌려서
다시 덤프를 뜬다. 상세 API 를 건당 1회씩 부르는 작업이라 시간이 걸린다.

---

## 적재한 사람: 덤프 갱신하는 법

데이터를 더 넣었거나 사진을 채운 뒤 팀에 다시 뿌릴 때.

`dump-recall.bat` 을 실행하면 이 폴더의 `recall-dump.sql` 을 덮어쓴다.
그 파일을 커밋하면 팀원들은 `git pull` 후 `load-recall.bat` 만 다시 돌리면 된다.

---

## 데이터 범위에 대해

지금 덤프는 공표일 2012-03-05 ~ 2026-09-16, 4,092건이다(10/1 적재).
9/21 덤프(778건)는 2023-07-12 부터였다. 제품안전정보센터 리콜 게시판
(safetykorea.kr/recall/recallBoard)에는 2012년 공표분까지 있고, 첫 글 번호가 4,249다(10/1 확인).

처음엔 "현재 판매 중인 상품"을 검증하는 물건이라 최근 리콜만 넣었다. 9/12 방향이
**구매이력 기반**(쿠팡 주문목록)으로 바뀌면서, 몇 년 전에 산 제품도 대상이 됐다.
그래서 과거분도 넣는다.

### Open API 가 과거분을 주는 방식 (10/1 실측)

| 조회 방법 | 결과 |
|---|---|
| 공표일 `publishDate` (`/recalls/range`) | **2023-07-12 이후만** 나온다. 2022-01 한 달 0건, 2023-07 은 12·14·24일 5건 |
| 품목명 `recallProductName=완구` (API 직접 호출) | 2026-09-16 ~ **2012-03-05** 공표분까지 나온다 |

즉 옛 자료는 API 안에 있는데 공표일로는 안 꺼내진다. 그래서 과거분은 품목명으로 훑는다.
옛 공표분은 `categoryName` 이 비어 있고 `productItemName`("어린이용품>완구")만 있어서,
저장할 때 그 값으로 `category_name` 을 채운다.

### 과거분 적재 — `POST /api/admin/sync/recalls/keywords`

파라미터 없이 부르면 아래 순서로 끝까지 훑는다. 서버 재시작 한 번이 필요하다(새 엔드포인트).

1. 출발 키워드: 어린이제품 34품목(제4차 어린이제품 안전관리 기본계획 붙임3)을 줄인 말
   (완구, 유모차, 섬유제품, 장신구 …) + 전기·생활용품 키워드(전기, 충전, 조명 …)
   + DB 에 이미 있는 품목명.
2. 키워드마다 목록 API 를 한 번 부르고 건별로 upsert 한다.
3. 받은 행의 품목명("기타완구(완구)" → "기타완구")을 새 키워드로 대기열에 붙인다.
   목록 API 는 페이징이 없고 1회 1,000건 상한이 있다고 알려져 있어서, 1,000건 이상 온
   키워드는 잘렸다고 보고 이 단계에서 잘게 나눠 다시 훑는다.
   (10/1 실측: 품목명 "용" 은 1,771건이 한 번에 왔다. 품목명 조회에는 1,000건 상한이 없을 수 있다 — 확실하지 않음.)
4. 상한에 안 걸리고 끝난 키워드를 포함하는 긴 키워드는 부르지 않는다
   ("완구"를 다 받았으면 "기타완구"는 새로 줄 게 없다). 호출 수가 크게 준다.

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `adminId` | (필수) | api_sync_log 에 남길 관리자 |
| `keywords` | 기본 목록 | 쉼표 구분. 특정 품목만 다시 돌릴 때 |
| `expand` | true | 받은 품목명으로 키워드를 넓혀 갈지 |
| `fromDb` | true | DB 에 있는 품목명도 출발 키워드로 쓸지 |
| `prune` | true | 4번 생략 규칙. 건드릴 일 없음 |
| `maxCalls` | 600 | 목록 API 최대 호출 수 |
| `withImages` | false | 사진은 `/recalls/images` 로 따로 |

### 절차 — Postman 컬렉션

`recall-backfill.postman_collection.json` 을 Postman 에 Import 한다.

1. 컬렉션 **Variables** 에서 `adminToken`(관리자 계정 로그인 accessToken), `adminId` 를 채운다.
   토큰은 Postman 에만 넣고 파일에 저장해서 커밋하지 않는다.
2. Postman **Settings → General → Request timeout** 이 0(무제한)인지 본다. 02 번은 수 분 걸린다.
3. 서버를 켠 상태에서 컬렉션 **Run**. 요청은 4개다.
   - `01` 공표일 2026-09-17~오늘 — 덤프 이후 새 공표분 보충
   - `02` 품목명 키워드 적재 — 과거분
   - `03` 보충 — 흔한 글자(용·기·품 …)로 넓게 훑어 02 가 못 찾은 품목명을 찾는다
   - `04` 적재 이력 확인
4. Console 에서 `02` 결과를 본다.
   - `신규` 가 새로 들어간 건수, `연도별 신규` 가 연도별 분포다. 2012 부터 찍혀야 한다.
   - `조회 실패` 키워드가 있으면 그것만 `keywords=그키워드&expand=false&fromDb=false` 로 다시 돌린다.
   - `남은 키워드` 가 0 이 아니면 `maxCalls` 를 올려 다시 돌린다(upsert 라 중복 없음).
   - `상한 걸린 키워드` 는 참고용이다. 결과 품목명으로 이미 잘게 다시 훑었다.
5. 게시판 첫 글 번호(총 건수)와 DB 건수를 비교한다. 차이가 크면 빠진 품목명을 게시판에서 찾아
   `keywords` 로 돌린다. 출발 키워드와 품목명이 하나도 안 겹치는 품목은 이 방식으로 못 찾는다.
6. 위 **확인** 쿼리로 빈 문자열 0 을 보고, `dump-recall.bat` 으로 덤프를 다시 떠서 커밋한다.
   이 README 맨 위 표의 건수·공표일 범위·기준일도 고친다.

### 10/1 적재 결과

| 실행 | 호출 | 신규 | DB 누계 | 비고 |
|---|---|---|---|---|
| 9/21 공표일 기간 적재 | — | 778 | 778 | 2023-07-12 ~ 2026-09-16 |
| 02 품목명(기본 키워드) | 296회 · 2분 7초 | 3,204 | 3,982 | 기존 778건도 전부 다시 찾음(갱신 778). 조회 실패·남은 키워드 0 |
| 03 흔한 글자 보충 | 213회 · 2분 14초 | 110 | 4,092 | "용"·"기"·"구" 가 1,000건 이상. 조회 실패·남은 키워드 0 |

02 의 연도별 신규: 2012:7 · 2013:109 · 2014:168 · 2015:426 · 2016:391 · 2017:306 · 2018:383 ·
2019:435 · 2020:332 · 2021:293 · 2022:247 · 2023:107.
게시판 4,249건과 157건 차이가 남는다. 원인은 확인하지 않았다(API 에 없는 글인지, 키워드가 안 닿는 품목명인지).
03 은 213회 호출에 110건이라 더 돌려도 얻는 게 적다고 보고 여기서 멈췄다.

`upsert` 라서 같은 `recall_uid` 는 덮어쓴다. 여러 번 돌려도 중복이 쌓이지 않는다.
컬럼 길이를 넘는 옛 값(긴 모델명 나열 등)은 뒤를 잘라 저장한다 — 행을 통째로 잃지 않게.

**알고 넣을 것.** 후보가 늘면 이름이 비슷한 옛 리콜이 '의심'으로 더 걸릴 수 있다(추측).
적재 전후로 같은 쿠팡 주문목록의 의심 건수를 비교해 둔다.
