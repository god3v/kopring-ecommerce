# AGENTS.md

이 저장소에서 작업하는 모든 AI 에이전트(Codex, Cursor, Claude Code 등)가 공유하는 컨벤션. Claude 전용 추가 지침은 [CLAUDE.md](./CLAUDE.md), 아키텍처 상세는 [docs/architecture.md](./docs/architecture.md).

## 프로젝트
**loopers-kotlin-spring-template** — Spring + Kotlin 멀티 모듈 커머스 백엔드 템플릿.

- Kotlin 2.0.20 / Java 21 / Spring Boot 3.4.4 / Gradle (Kotlin DSL)
- MySQL 8 · Redis 7 (master+readonly) · Kafka 3.5 (KRaft)

## 시작 전 읽을 것
1. [docs/architecture.md](./docs/architecture.md) — 모듈/계층 구조 한눈에 보기
2. [docs/guideline/tdd-guideline.md](./docs/guideline/tdd-guideline.md) — TDD + Tidy First 사이클
3. 현재 작업 폴더의 `plan.md` — 진행 중인 체크리스트

## 요청 처리 흐름 — 구현 전 설계 점검
작업 요청을 받으면 곧바로 코드를 작성하지 않는다. 먼저 요청이 아키텍처·구조 측면에서 타당한지 점검하고, 쟁점이 있으면 사용자와 합의한 뒤 구현한다.

1. **매핑** — 요청을 4계층·모듈 위계 어디에 배치할지, 책임이 어느 계층/객체에 속하는지 한두 줄로 정리한다.
2. **점검** — 다음과 충돌하는 지점이 있는지 본다: 계층 경계·의존 방향, 트랜잭션/조율 소유(`Facade`), 도메인 캡슐화(Tell Don't Ask), 기존 컨벤션·네이밍, 결합도.
3. **논의** — 쟁점·대안·트레이드오프를 간결히 제시하고 **권장안까지 함께** 낸다(선택지 나열로 끝내지 않는다). 합의 전에는 구현을 시작하지 않는다.
4. **예외** — 구조에 영향을 주는 요청에만 적용한다. 새 도메인/계층 배치, 의존 방향, 트랜잭션 경계, 책임 위치가 걸리지 않는 사소·기계적 변경은 합리적 기본값으로 바로 진행하되 선택한 바를 한 줄로 밝힌다. 점검은 한 번, 짧게 — 모든 요청을 토론으로 막지 않는다.

## 작업 원칙
- **TDD + Tidy First**: Red → Green → Refactor. 구조 변경과 행위 변경을 같은 커밋에 섞지 않는다.
- **계층 경계**: `interfaces → application ← infrastructure`, `application → domain`. `domain` 은 스프링/JPA 를 모른다.
- **트랜잭션 경계 & 조율**: `application.Facade` 가 Repository 호출과 `@Transactional` 을 단일 소유.
- **순수 도메인 헬퍼는 허용**: 정책/계산기/스펙(예: `PasswordPolicy`, `OrderPriceCalculator`) 은 무상태·인자 기반이면 `domain` 에 둔다.
- **Tell, Don't Ask**: 도메인 상태를 밖에서 묻고(`isXxx()`) `if` 로 분기하기보다, 규칙과 그 위반 예외를 도메인 객체의 행위 메서드로 캡슐화한다. (예: `if (coupon.isExpired(now)) throw …` → `coupon.ensureIssuable(now)`)
- **null-safety 최우선**. `!!` 는 근거 코멘트와 함께만.
- **라이브러리 버전**은 `gradle.properties` 단일 출처.
- **Skill 우선**: 작업 요청이 등록된 skill(`/requirements`, `/api-spec`, `/test-cases`, `/tdd`, `/design-qna`, `/analyze-query` 등)에 해당하면 직접 처리하기 전 해당 skill 을 활용한다.
- **작업 후 검증 필수**: 코드 변경 뒤에는 빌드·테스트(`./gradlew ktlintCheck test`)를 수행해 통과를 확인한다.

## 글쓰기 · 화법
세션 대화, 코드 주석, 문서 전부에 적용한다. 한국어로 자연스럽게, **무엇을 왜 바꿨는지**를 중심으로 쓴다.

- **번역기체 금지**: 영어를 그대로 옮긴 문장은 쓰지 않는다. 한국어로 먼저 생각하고 쓴다.
- **추상 명사보다 동사**: "이벤트 발행의 처리를 수행한다" 가 아니라 "이벤트를 발행한다".
- **어색한 동사 결합 금지**: "TDD 를 올린다", "테스트를 태운다" 같은 조합 대신 "TDD 로 진행한다", "테스트를 돌린다" 처럼 실제로 쓰는 말로.
- **직관적이지 않은 용어 금지**: "봉투(envelope)", "additive" 처럼 바로 안 와닿는 말은 쓰지 않는다. 대상의 실제 이름을 쓰거나, 풀어서 설명한다.
- **괄호 남발 금지**: 부연은 괄호로 덧붙이지 말고 문장으로 녹인다. 꼭 필요할 때만 하나.
- **주석은 역할만**: "원래 X 였는데 Y 로 바꿈" 같은 수정 이력은 주석에 남기지 않는다 — 그건 커밋 메시지의 몫이다. 주석에는 이 코드가 **지금 무슨 일을 하는지**만 짧게 적는다.
- **감정어·경험담·의인화 금지**: 기술 문서는 사실만 건조하게 적는다. "유혹", "땜질", "체감했다", "머릿속으로 계산이 안 됐다", "코드가 ~하고 싶어한다" 같은 표현은 쓰지 않는다. 화자를 연기하지 말고 무슨 일이 일어나는지만 서술한다. ("멱등성 처리를 미루고 싶은 유혹이 있다" → "멱등성 처리를 뒤로 미루면 중복 실행 위험이 남는다")

## 도구
- 포맷: `./gradlew ktlintCheck` / `./gradlew ktlintFormat` — 커밋 전 통과 필수.
- 테스트: `./gradlew test` 또는 `./gradlew :apps:commerce-api:test`. 통합 테스트는 `modules:jpa` / `modules:redis` 의 `testFixtures` Testcontainers 재사용.
- 실행: `./gradlew :apps:commerce-api:bootRun` (사전에 `docker-compose -f ./docker/infra-compose.yml up`).
- 커버리지: `./gradlew jacocoTestReport`.

## 커밋 / PR
- **커밋은 사용자 승인 없이 수행하지 않는다.** 코드 변경 후 간결한 커밋 메시지를 제안만 하고, 실제 커밋은 사용자 확인을 받은 뒤 진행한다.
- 커밋 메시지: `type(scope): 한국어 요약` 한 줄 (type: `feat`/`fix`/`refactor`/`test`/`docs`/`chore`, scope: 도메인/모듈명).
- 한 커밋은 하나의 논리적 단위. 커밋 메시지에 구조/행위 변경 여부 명시.
- PR 본문: 변경 목적 → 핵심 변경점 → 리스크 → 검증 방법 (4~8줄).
- CodeRabbit 자동 리뷰. `wip`/`draft` 키워드가 제목에 있으면 스킵.
- 민감정보(`.env`, 자격증명, `application*.yml` 내 비밀)는 절대 커밋하지 않는다.

## 자주 쓰는 패턴
- 신규 도메인 추가: 4계층을 추가한다.
  - `domain.<aggregate>` — 순수 모델/값객체/도메인 규칙, domainService, domainRepository (I)
  - `application.<usecase>` — `Facade`, `command/`, `result/`
  - `infrastructure.<aggregate>` — `Entity`, `JpaRepository`, `RepositoryImpl`
  - `interfaces.api.<resource>` — `Controller`, `ApiSpec`, `Dto`
- 신규 모듈 추가: `settings.gradle.kts` 의 `include(...)` 등록 + `apps`/`modules`/`supports` 중 적절한 위치.
- 컨테이너 프로젝트(`apps`, `modules`, `supports`) 자체에는 task 가 실행되지 않는다 — 하위 모듈을 명시한다.

## 절대 금지
- `apps/*` 끼리의 직접 의존.
- `domain` 에서 스프링/JPA 어노테이션 참조.
- `build.gradle.kts` 안에 라이브러리 버전 하드코딩.
- 테스트마다 새 Testcontainer 즉흥 기동 (testFixtures 재사용).
- 운영 영향 설정(타임아웃, 커넥션 풀, 로깅 레벨) 변경을 PR 본문에 근거 없이 포함.
