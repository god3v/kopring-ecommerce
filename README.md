# E-Commerce Platform (Spring + Kotlin)

10주 동안 이커머스 백엔드를 단계적으로 구축했다. 단순 CRUD 를 넘어 **설계 → 동시성 → 읽기 성능 → 회복탄력성 → 이벤트 → 유량 제어 → 실시간 집계 → 배치 집계**까지, 실제 서비스가 마주치는 문제를 작은 스케일로 직접 풀었다.

핵심 키워드: `TDD` `헥사고날 아키텍처` `동시성 제어` `읽기 최적화` `Resilience4j` `Transactional Outbox` `Kafka` `Redis ZSET` `Spring Batch` `Materialized View`

## 프로젝트 개요

### 비즈니스 도메인

- 회원 / 브랜드 / 상품 / 좋아요
- 주문 · 쿠폰 · 결제 (외부 PG 연동)
- 주문 대기열 (유량 제어)
- 상품 랭킹 (일간 실시간 · 주간/월간 배치)

### 기술 스택

- **Language** : Kotlin 2.0.20 / Java 21
- **Framework** : Spring Boot 3.4.4, Spring Batch, Spring Data JPA (+ QueryDSL)
- **Database** : MySQL 8.0
- **Cache / Queue / Ranking** : Redis 7 (master + readonly replica)
- **Messaging** : Kafka 3.5 (KRaft)
- **Resilience** : Resilience4j (Retry, CircuitBreaker)
- **Test** : JUnit 5, Testcontainers, springmockk, k6 (부하 측정)
- **Monitoring** : Prometheus, Grafana
- **Build** : Gradle (Kotlin DSL), ktlint, Jacoco

## 시스템 아키텍처

```
[commerce-api]        주문·결제·상품·랭킹 API. 도메인 이벤트를 Transactional Outbox 로 발행
      │ Kafka (catalog-events · order-events)
      ▼
[commerce-streamer]   이벤트 소비 (consumer group 분리)
      ├─ metrics 그룹 : product_metrics · product_metrics_hourly 적재 (멱등 표식 + 같은 트랜잭션)
      ├─ ranking 그룹 : Redis ZSET rank:all:{yyyyMMdd} 가중 점수 누적 (Lua 원자 멱등)
      └─ coupon 그룹  : 선착순 쿠폰 발급
[commerce-batch]      productRankJob (매일, targetDate 파라미터)
      └─ 시간별 집계 → 주간·월간 합산 → 가중 점수 TOP 100 MV 원자 교체
[commerce-api]        GET /rankings?period=DAILY|WEEKLY|MONTHLY
      ├─ DAILY   → Redis ZSET (실시간)
      └─ WEEKLY/MONTHLY → MV 테이블 (배치 사전 집계)
```

### 멀티 모듈 구조

```
Root
├── apps ( 실행 가능한 SpringBootApplication — 상호 의존 금지 )
│   ├── 📦 commerce-api        # 커머스 API 서버
│   ├── 📦 commerce-streamer   # Kafka 컨슈머 (집계·랭킹·쿠폰)
│   ├── 📦 commerce-batch      # Spring Batch (주간·월간 랭킹)
│   └── 📦 pg-simulator        # 외부 PG 시뮬레이터 (지연·실패 재현)
├── modules ( reusable configuration — 도메인 의존 금지 )
│   ├── 📦 jpa / 📦 redis / 📦 kafka
└── supports ( add-on )
    ├── 📦 jackson / 📦 logging / 📦 monitoring
```

### 계층 구조 — 헥사고날의 의도된 비대칭

```
interfaces.api  →  application(Facade·port)  ←  infrastructure
                        │
                        ▼
                     domain (순수 Kotlin — Spring/JPA 를 모른다)
```

- **outbound 만 포트**를 둔다. 외부 게이트웨이·저장소는 `application.port` 뒤에 격리하되, inbound port(usecase 인터페이스)는 채택하지 않았다 — usecase 와 1:1 인 인터페이스는 표면적 이득보다 코드량 비용이 크다고 판단했다.
- 트랜잭션 경계와 도메인 조율은 `Facade` 가 단일 소유한다. 상세: [docs/architecture.md](./docs/architecture.md)

## 10주 히스토리

각 주차의 목표·설계 결정·검증 기록은 `.docs/weekN/` 에, 도메인별 요구사항·API 명세는 [docs/domain](./docs/domain) 에 있다.

| 주차 | 주제 | 핵심 문제와 해결 | 기록 |
|---|---|---|---|
| 01 | 테스트 가능한 구조 · TDD | 회원 도메인을 Red→Green→Refactor 로 구축. 도메인 검증과 인프라 관심사 분리 | [#3](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/3) [#6](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/6) |
| 02 | 도메인 모델링 · 설계 문서 | 이커머스 요구사항 분석. 도메인 계층의 Repository·Service 의존을 제거하고 조율을 application 으로 이전 | [#8](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/8) [#11](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/11) |
| 03 | 유스케이스 구현 | 브랜드·상품·좋아요·주문·쿠폰 API. 객체 협력 설계와 Facade 조율 | [#13](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/13) [#17](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/17) [#18](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/18) |
| 04 | 트랜잭션 · 동시성 제어 | 좋아요/주문/결제의 경합을 도메인별 전략으로 제어. 결제 트랜잭션 분리 | [#19](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/19) [#21](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/21) · [QnA](./.docs/week4/qna.md) |
| 05 | 읽기 성능 최적화 | k6 로 병목을 측정하고 인덱스·비정규화·캐시로 해결. 추측이 아니라 수치로 결정 | [#23](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/23) · [벤치마크](./.docs/week5/benchmark.md) |
| 06 | 외부 PG 연동 · 회복탄력성 | 신뢰할 수 없는 외부 결제의 지연·실패가 시스템 전체로 번지지 않게 격리 | [#26](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/26) · [결정 기록](./docs/domain/payment/resilience-decisions.md) |
| 07 | 이벤트 기반 아키텍처 | 한 트랜잭션에 묶인 흐름을 "지금 꼭"과 "나중에"로 분리 — ApplicationEvent → Kafka + Transactional Outbox + 멱등 소비 | [#28](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/28) [#29](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/29) [#30](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/30) |
| 08 | Redis 주문 대기열 | 감당 가능한 속도로만 주문을 통과시키는 관문. 순번·예상 대기시간으로 이탈 방지 | [#31](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/31) |
| 09 | 실시간 상품 랭킹 | Kafka 이벤트로 Redis ZSET 에 가중 점수 실시간 누적. Lua 원자 멱등, 콜드 스타트 이월, RDB 원본 기반 자가 복구 | [#32](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/32) [#33](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/33) · [설계 기록](./.docs/week9) |
| 10 | Spring Batch · Materialized View | 주간·월간 랭킹을 배치 사전 집계로. 멱등 재집계, 실패 스텝 재시작, MV 원자 교체 | [#34](https://github.com/god3v/loop-pack-be-l2-vol4-kotlin/pull/34) · [구현 정리](./.docs/week10/구현정리.md) |

## 설계 하이라이트

#### 1. 동시성 제어 — 도메인마다 다른 전략

정답 하나를 모든 곳에 적용하지 않고, 각 자원의 실패 비용에 맞는 전략을 골랐다.

| 자원 | 전략 | 이유 |
|---|---|---|
| 재고 차감 | 비관적 쓰기 락 + id 정렬 조회 | 음수 재고 절대 방지, 다건 잠금의 데드락 회피 |
| 쿠폰 사용 | 비관적 쓰기 락 | 중복 사용 방지 |
| 결제 정산 | 거래 식별자 기준 비관적 락 | 콜백·폴링이 동시에 도착해도 상태 전이를 직렬화 |
| 랭킹 반영 | Redis Lua 원자 실행 (멱등 표식 + ZINCRBY) | at-least-once 재전달에서 유실도 중복도 없이 |

#### 2. 외부 장애는 격리하고, 상태는 수렴시킨다

PG 호출은 타임아웃 · 재시도(Retry) · 서킷브레이커로 감싸 지연 전파를 끊었다. 결제 상태는 콜백과 폴링 어느 쪽이 먼저 와도 같은 결과로 수렴하도록 정산 경로를 직렬화했다. 외부 시스템은 "언젠가 실패한다"를 전제로 설계했다. → [resilience-design-doc](./docs/domain/payment/resilience-design-doc.md)

#### 3. 이벤트 발행의 원자성 — Transactional Outbox

도메인 상태 변경과 이벤트 발행이 어긋나면 집계·랭킹이 전부 틀어진다. 이벤트를 비즈니스 트랜잭션 안에서 outbox 테이블에 커밋하고, 릴레이가 Kafka 로 내보낸다. 소비 쪽은 eventId 멱등 표식을 처리 결과와 같은 트랜잭션에 남겨 at-least-once 를 정확히 1회 처리로 수렴시켰다.

#### 4. 랭킹 — 창 길이가 아키텍처를 가른다

같은 "랭킹"이어도 시간 창에 따라 답이 다르다. 일간은 Redis ZSET 실시간 누적(창이 하루라 TTL·보정이 감당 가능), 주간·월간은 Spring Batch 사전 집계(실시간성이 불필요하고 정확성·비용이 우선). 하나의 API 가 `period` 파라미터로 두 저장소를 갈아탄다.

- 원본은 시간별 신호 집계(`product_metrics_hourly`) — **가중치가 아닌 신호 개수를 저장**해, 가중치가 바뀌어도 원본이 유효하고 Redis 유실 시 재구축의 근거가 된다.
- 배치 쓰기는 기간 키 단위 delete + insert — **몇 번 돌아도 결과가 같다.** 실패하면 다시 돌리면 되고, 실패 스텝부터의 재시작도 검증했다.
- MV 교체는 한 트랜잭션 — 갱신 도중 조회가 반쯤 지워진 판을 보지 않는다.

#### 5. 앱 간 계약은 테스트로 고정한다

Kafka 토픽, Redis 키(`rank:all:{yyyyMMdd}`), MV 테이블 스키마, 기간 키(`2026W30`)는 컴파일 타임 보장이 없는 와이어 계약이다. 각 앱이 계약 코드를 각자 소유하되, 양쪽 단위 테스트가 같은 값을 물고 있어 한쪽이 포맷을 바꾸면 즉시 깨진다. 규약 자체는 설계 문서의 표가 단일 출처다.

#### 6. 도메인은 프레임워크를 모른다

`domain` 패키지는 순수 Kotlin 이다 — Spring·JPA import 0. 규칙은 `coupon.ensureIssuable(now)` 처럼 도메인 객체의 행위로 캡슐화하고(Tell, Don't Ask), 상태를 밖에서 묻고 분기하지 않는다. 무상태 정책·계산기(가중 점수, 기간 키)도 도메인에 두고 단위 테스트로 고정했다.

## 테스트 전략

- **TDD + Tidy First** — 기능마다 Red → Green → Refactor. 구조 변경과 행위 변경은 커밋을 분리한다.
- **계층별 검증** — 순수 계산은 단위 테스트, 어댑터는 Testcontainers 통합 테스트, 흐름은 E2E. 랭킹 배치는 순수 계산 → 엔티티 → 스텝 → 잡 관통 → 재시작 → API E2E 까지 9개 층으로 검증했다.
- **Testcontainers 재사용** — MySQL·Redis·Kafka 컨테이너 설정을 `modules` 의 testFixtures 로 공유한다. 테스트마다 컨테이너를 즉흥 기동하지 않는다.
- **부하 측정** — 읽기 최적화(W5)·대기열(W8)·랭킹(W9)은 k6 시나리오로 수치를 남기고 판단했다.

```bash
./gradlew ktlintCheck test          # 전체 검증
./gradlew :apps:commerce-api:test   # 모듈 단위
```

## Getting Started

### 사전 준비

```bash
make init    # pre-commit hook (ktlint)
```

### 인프라 기동

```bash
docker-compose -f ./docker/infra-compose.yml up    # MySQL · Redis · Kafka
```

### 애플리케이션 실행

```bash
# API 서버
./gradlew :apps:commerce-api:bootRun

# Kafka 컨슈머 (집계·랭킹·쿠폰)
./gradlew :apps:commerce-streamer:bootRun

# 랭킹 배치 — targetDate 가 속한 ISO 주·달력 월을 재집계하고 종료한다
./gradlew :apps:commerce-batch:bootRun --args="--job.name=productRankJob targetDate=2026-07-22"
```

### 모니터링 (선택)

```bash
docker-compose -f ./docker/monitoring-compose.yml up
# Grafana: http://localhost:3000 (admin / admin)
```

## 문서 맵

| 위치 | 내용 |
|---|---|
| [docs/architecture.md](./docs/architecture.md) | 모듈 위계 · 4계층 구조 · 의존 방향 |
| [docs/domain/*](./docs/domain) | 도메인별 요구사항 · API 명세 · 테스트 플랜 |
| [.docs/weekN/](./.docs) | 주차별 목표(goal) · 진행 계획(plan) · 설계 결정과 검증 기록 |
| [docs/guideline](./docs/guideline) | TDD 가이드 · 문서 템플릿 |
