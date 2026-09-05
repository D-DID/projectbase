# recallcheck 스켈레톤 적용 안내

이 ZIP 은 projectbase 프로젝트 루트에 그대로 덮어쓰면 된다.

## 적용 순서

1. **기존 인증 코드부터 옮길 것** (덮어쓰기 전에)
   - `com/meta/projectbase/auth/util/JwtUtil.java`      → `security/JwtProvider.java`
   - `com/meta/projectbase/auth/config/SecurityConfig.java` → `config/SecurityConfig.java`
   - `com/meta/projectbase/auth/config/PasswordEncoderConfig.java` → `config/SecurityConfig.java` 에 병합됨
   - `com/meta/projectbase/auth/domain/User.java`       → `entity/User.java` (컬럼 확인)
   - `com/meta/projectbase/auth/domain/UserRole.java`   → `entity/enums/Role.java`
   - `com/meta/projectbase/common/domain/TimeStamped.java` → `entity/BaseTimeEntity.java`

2. ZIP 을 프로젝트 루트에 압축 해제 (덮어쓰기)

3. **다음 폴더/파일 삭제**
   - `src/main/java/com/meta/`
   - `src/test/java/com/meta/`
   - `projectbase.iml` (IntelliJ 가 다시 만든다)
   - `HELP.md`

4. DB 스키마 생성
   ```sql
   CREATE DATABASE recallcheck DEFAULT CHARACTER SET utf8mb4;
   ```

5. IntelliJ 에서 Gradle 재로드 (`settings.gradle` 의 rootProject.name 이 바뀌었음)

## 남은 확인 항목

- `entity/Recall.java`, `RecallFile.java`, `Certification.java`, `ApiSyncLog.java`, `User.java`
  의 컬럼은 확정 DDL 과 대조해서 맞출 것 (verification / extraction / match_result 3개는 ERD 기준으로 확정됨)
- `openapi.safety-korea.service-key` 에 발급받은 인증키 기입
- `client/dto/*ApiResponse.java` 의 `@JsonProperty` 필드명은
  Open API 인터페이스 설계서 v2.0 의 실제 응답과 대조해서 확정할 것

## 구현 순서 권장

1. 엔티티 8개 + 리포지토리 매핑 확정
2. `SafetyKoreaRecallClient` + `RecallSyncService` → 실제 리콜 데이터 적재
3. 수동 입력 경로(FR-008)만으로 매칭 → 4단계 판정까지 end-to-end 완성
4. `MatchingService` 임계값 튜닝
5. URL 파싱 (FR-005)
6. OCR (FR-006) — 마지막
