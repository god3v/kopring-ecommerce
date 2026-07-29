# CLAUDE.md

Claude Code 가 본 저장소에서 작업할 때 따르는 컨텍스트와 규칙. 일반 AI 에이전트 공통 가이드는 [AGENTS.md](./AGENTS.md), 아키텍처 상세는 [docs/architecture.md](./docs/architecture.md).

## 프로젝트
**loopers-kotlin-spring-template** — Spring + Kotlin 멀티 모듈 커머스 백엔드 템플릿.

- Kotlin 2.0.20 / Java 21 / Spring Boot 3.4.4
- MySQL 8 · Redis 7 (master+readonly) · Kafka 3.5 (KRaft)
- 빌드: Gradle (Kotlin DSL) · ktlint · Jacoco

## 모듈 위계
- `apps/*` 실행 가능한 SpringBoot 앱 (commerce-api / batch / streamer). 서로 의존 금지.
- `modules/*` reusable config (jpa, redis, kafka). 도메인 의존 금지.
- `supports/*` add-on (jackson, logging, monitoring).
- 라이브러리 버전은 `gradle.properties` 에만 정의 — `build.gradle.kts` 하드코딩 금지.

## 패키지 4계층 (commerce-api · 엄격 헥사고날)
```
com.loopers
├── domain.<aggregate>          Model · 값객체 · 도메인 규칙 (순수 Kotlin)
├── application.<usecase>       Facade · Command · Result · port  ← outbound port
├── infrastructure.<aggregate>  Entity · JpaRepository · RepositoryImpl         ← outbound adapter
├── interfaces.api.<resource>   Controller · ApiSpec · Dto
└── support.error               CoreException · ErrorType
```
- 의존 방향: `interfaces → application ← infrastructure`, 그리고 `application → domain` (단방향).
- `domain` 은 스프링/인프라/Repository 를 모른다. 순수 모델과 규칙만 보유.
- `application.<usecase>.port` 에는 외부 게이트웨이(`PaymentGateway` 등) 가 위치한다.
- **inbound port (usecase interface) 는 본 프로젝트에서 채택하지 않는다.** `interfaces.api` 의 Controller 가 `application.Facade` 를 구상 의존으로 직접 호출한다. "outbound 만 헥사고날, inbound 는 단순 호출" 은 의도된 비대칭이다 — usecase 가 사실상 1:1 매핑이라 inbound 인터페이스의 표면적 이득(어댑터 교체·mocking) 보다 코드량 비용이 크다고 판단했다.
- application 진입점은 **`*Facade`** 로 명명한다.
- 조율/협력 로직은 `application.Facade` 가 트랜잭션 경계와 함께 단일 소유한다.
- 무상태 순수 도메인 헬퍼 (`PasswordPolicy` · `OrderPriceCalculator` 같은 정책/계산기/스펙) 는 `domain` 에 둘 수 있다. 인자만 받아 계산/검증하며, Repository/Spring 의존은 금지.
- `interfaces.api` 는 검증 + 응답 조립만.

## 개발 규칙
- **ktlint** 단일 포맷터. 커밋 전 `make init` 으로 pre-commit hook 활성화.
- **Kotlin**: null-safety 최우선, JPA 엔티티는 `data class` 지양, 도메인/인프라 예외 분리.
- **Controller**: Bean Validation + 응답 조립만, 엔티티 직접 노출 금지.
- **Service**: `@Transactional` 경계/전파/readOnly 명시, 외부 호출은 타임아웃·멱등성.
- **Repository**: N+1·fetch join·인덱스·Lazy 로딩 경계 점검.
- 민감정보를 `application*.yml` 에 직접 커밋하지 않는다.

## 테스트
- JUnit 5 + springmockk + Instancio.
- 통합 테스트는 `modules:jpa` / `modules:redis` 의 `testFixtures` Testcontainers 재사용 — 새 컨테이너 즉흥 기동 금지.
- `Asia/Seoul`, `spring.profiles.active=test`, `maxParallelForks=1` 강제.

## 커밋 / PR
- 기능 단위 커밋. ktlint 통과 필수. CodeRabbit 자동 리뷰 (`wip`/`draft` 제목이면 스킵).
- PR 본문: 변경 목적 → 핵심 변경점 → 리스크 → 검증 방법 (4~8줄).

## 작업 방법론 — Kent Beck TDD & Tidy First
신규 기능/결함 수정은 **Red → Green → Refactor** 사이클. 구조 변경과 행위 변경은 같은 커밋에 절대 섞지 않는다 — 구조 먼저, 별도 커밋. 전체 가이드: 📄 [docs/guideline/tdd-guideline.md](./docs/guideline/tdd-guideline.md). 사용자가 "go" 라고 말하면 `plan.md` 의 다음 미체크 테스트부터 진행한다.

## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.
