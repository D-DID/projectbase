# 전체 소스 적용본 (엔티티 DDL 정합 + JWT 인증 구현 포함)

앞서 보낸 recallcheck-entities.zip / recallcheck-auth.zip 이 적용되지 않은 것으로 확인되어
두 변경을 모두 반영한 **전체 소스 트리**를 한 번에 담았다. 이것만 덮어쓰면 된다.

## 적용 순서

1. **파일 하나 삭제** (압축 해제로는 지워지지 않음)
   src\main\java\com\underfaker\recallcheck\entity\enums\SyncStatus.java
   → 확정 DDL 의 api_sync_log 에는 status 컬럼이 없다. ApiType 으로 대체됨.

2. 이 ZIP 을 프로젝트 루트에 압축 해제 (덮어쓰기)
   - src\ 전체 (java 81개)
   - src\main\resources\schema.sql       (신규)
   - src\main\resources\application.properties.example
   - application.properties 는 들어있지 않다. 이미 반영돼 있고 인증키가 들어있어 ZIP 에 넣지 않았다.

3. **DB 재생성** — MySQL 에서 src\main\resources\schema.sql 전체 실행
   (파일 안에 DROP TABLE / CREATE DATABASE 포함. 기존 recallcheck 는 잘못된 컬럼이라 버린다)

4. IntelliJ Gradle 재로드 → 실행
   ddl-auto=validate 라서 엔티티와 스키마가 어긋나면 기동 시 바로 에러로 알려준다.

5. 동작 확인 (Postman)
   POST http://localhost:8080/api/auth/signup
   {"email":"test@test.com","password":"test1234","username":"테스터"}

   POST http://localhost:8080/api/auth/login
   {"email":"test@test.com","password":"test1234"}
   → accessToken 발급되면 성공

6. 그다음 커밋 & 푸시

## 관리자 계정
회원가입은 항상 ROLE_USER. 관리자가 필요하면:
   UPDATE user SET user_role = 'ADMIN' WHERE email = 'test@test.com';
