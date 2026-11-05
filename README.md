# UnderFaker

**구매한 유아·어린이 제품의 리콜 여부를 확인하는 서비스**

[프로젝트 상세 문서 (Notion)](https://fuchsia-school-5a5.notion.site/UnderFaker-3d9b59c9d93480a4a435ee58c1391a05)

쿠팡 구매이력 또는 직접 입력한 제품 정보를 국가기술표준원의 리콜 공표문과 대조하여 제품의 리콜 관련 정보를 확인합니다.

## 주요 기능

- 제품명, KC 인증번호, 모델명 기반 리콜 검증
- Chrome Extension을 통한 쿠팡 구매이력 수집 및 검증
- Google Vision 기반 제품 사진 검색
- 쇼핑몰 상세페이지 및 리콜 공표문 우클릭 검증
- 검증 결과 및 근거 이력 관리

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java, Spring Boot 4 |
| ORM | Spring Data JPA |
| Database | MySQL, H2 |
| Frontend | HTML, CSS, JavaScript |
| Extension | Chrome Extension |
| External API | SafetyKorea API, Google Vision |
| Test | JUnit, Playwright |
| Build | Gradle |

## 프로젝트 구조

| 경로 | 설명 |
|---|---|
| `src/main/java/` | Spring Boot 백엔드 |
| `src/main/resources/static/` | 웹 프론트엔드 |
| `db/` | 리콜 데이터 및 적재 스크립트 |
| `docs/` | API 명세 및 테스트 문서 |
| `underfaker-extension/` | Chrome Extension |

## 실행 방법

자세한 설정은 `SETUP.md`를 참고합니다.

```bash
gradlew bootRun
