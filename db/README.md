# 리콜 데이터 공유

국가기술표준원 SafetyKorea Open API 에서 받은 리콜 데이터다.
각자 적재할 필요 없이 이 덤프를 넣으면 된다.

| 항목 | 값 |
|---|---|
| 리콜 건수 | 778 |
| 공표일 범위 | 2023-07-12 ~ 2026-09-16 |
| 포함 테이블 | `recall`, `recall_file` |
| 정규화 검증 | `normalized_cert_num` / `normalized_model_name` 빈 문자열 0건 |
| 기준일 | 2026-09-21 |

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

`778 / 0 / 0 / 20230712 / 20260916` 이 나와야 한다.

**빈 문자열 두 칸이 0 인지 꼭 볼 것.** 여기가 0 이 아니면 후보조회가
`LIKE '%%'` 로 번역돼서 리콜 전건이 후보로 끌려온다. 인증번호 없는 상품 하나가
778건 전부와 매칭되는 사고가 여기서 난다.

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

공표일이 2023-07-12 부터인 건 그 이전을 안 긁었기 때문이다.
연 250건 안팎으로 일정하게 나오므로 더 과거 데이터도 존재한다.

지금 범위로 멈춘 이유는, 이 제품이 **현재 판매 중인 상품**을 검증하는 물건이라
최근 리콜이 훨씬 중요하고, 2010년대 리콜은 지금 쿠팡에서 파는 물건과 매칭될 일이
거의 없기 때문이다. 과거 전체가 필요해지면 아래를 400일 이하 구간으로 끊어서 돌린다.

```
POST /api/admin/sync/recalls/range?adminId={관리자ID}&from=20220601&to=20230630&withImages=false
```

`upsert` 라서 같은 `recall_uid` 는 덮어쓴다. 여러 번 돌려도 중복이 쌓이지 않는다.
