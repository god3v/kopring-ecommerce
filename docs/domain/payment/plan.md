# payment 도메인 — TDD 플랜

> **입력 명세**: [requirements.md (v0.1)](./requirements.md) · [api-spec.md](./api-spec.md)
> **방법론**: [Kent Beck TDD & Tidy First](../../guideline/tdd-guideline.md)
> **시작일**: 2026-06-23
>
> Round 6 — 동기·항상성공 mock 결제를 **비동기 실(real) PG 연동**으로 전환하고, 그 위에 Timeout · CircuitBreaker · Fallback · 폴링 복구를 입힌다.

---

## 사용 규칙

- **한 줄 = 하나의 실패 테스트 = 하나의 Red → Green → Refactor 사이클**
- **단순한 것 → 복잡한 것** 순으로 배치
- 구현 디테일이 아닌 **관찰 가능한 행위**로 서술한다
- 작업 중 떠오른 케이스는 즉시 추가한다 (살아있는 문서)

| 마크 | 의미 |
|---|---|
| `- [ ]` | 미시작 |
| `- [~]` | 진행 중 (Red 만) |
| `- [x]` | 완료 |

---

## 0. 스코프 / 결정

### 핵심 결정 (requirements QnA 합의)

- **진입점** — 명시 API 일원화. 자동 결제 트리거를 제거하고, 회원이 `POST /api/v1/payments` 로 결제한다.
- **결과 반영** — 콜백이 주 경로, 폴링이 안전망.
- **Fallback** — 정산 시점에 갈린다. 영구 실패(잘못된 카드·한도 초과)는 즉시 `FAILED`+보상, 일시 장애(통신 실패·타임아웃·CB Open)는 `REQUESTED` 유지 후 복구. 요청 응답은 두 경우 모두 `REQUESTED`.
- **스코프** — 결제 전 생애주기를 payment 가 소유. order 는 결제대기 주문 생성까지로 축소.

### 주문 생명주기 (이번 라운드 변경)

```
CREATED → PAYMENT_PENDING → PAID / PAYMENT_FAILED
```

주문 생성 직후는 `CREATED`. 결제 요청이 접수되면 `PAYMENT_PENDING` 으로 전이한다. 결제 가능 판정과 전이는 주문 도메인이 캡슐화한다(`validatePayable` · `markPaymentPending`).

### 구조

- `domain.payment` — `Payment` + `PaymentStatus(REQUESTED/APPROVED/FAILED/CANCELED)`. 전이: `approve` · `fail` · `cancel` · `accept`.
- `application.payment` — `PaymentFacade` 단일 진입점이 요청(`pay`) · 정산(`settle`) · 복구(`sync`) 를 소유한다.
- 포트 — `application.payment.port.PaymentGateway(request·getTransaction·getByOrder)`, 어댑터는 `PgSimulatorPaymentGateway`(RestClient).
- 제거됨 — 동기 `PaymentGateway.charge` · `AlwaysSuccessPaymentGateway` · `PaymentResult` · `PaymentInitiator` · `PaymentSettler` · `PaymentCanceler` · 자동 트리거(`OrderPlacedEvent`).

### 설계 메모

- **`pay` 는 단일 `@Transactional`** — 주문 잠금 → `validatePayable` → `markPaymentPending` → 외부 `request` → `accept` → save. 외부 호출 *전에* 결제를 먼저 영속하므로, 호출이 실패해도 `REQUESTED` 레코드가 남아 복구 대상이 된다. 외부 호출은 트랜잭션 안이지만 타임아웃이 락 보유 시간을 제한한다.
- **`settle` 은 멱등** — `transactionKey` 로 결제를 찾아 비관 락을 잡고, `REQUESTED` 일 때만 `PgTransaction(SUCCESS/FAILED/PENDING)` 으로 분기한다.
- **취소·환불 보류** — pg-simulator 에 환불 API 가 없어 외부 환불이 불가능하다. 외부 `refund` 와 취소 오케스트레이션은 제거했고, 도메인 전이 `Payment.cancel()` 만 향후 대비로 보존한다(현재 미참조).

---

## Phase 1 — 도메인 모델 (`com.loopers.domain.payment`)

> 상태 전이(approve/fail/cancel)·멱등 규칙은 베이스라인에서 완료. 아래는 비동기 전환으로 새로 필요한 행위.

- [x] 결제를 요청하면 거래 식별자 없이 `REQUESTED` 상태로 생성된다
- [x] 요청 상태 결제에 외부 거래 식별자를 접수 기록할 수 있다
- [x] 거래 식별자를 받지 못한 결제도 요청 상태로 유효하게 존재한다
- [x] 한 번 접수된 거래 식별자는 다른 값으로 덮어쓰이지 않는다

## Phase 2 — 도메인 서비스

> payment 는 도메인 서비스를 두지 않는다. 조율은 `application.payment`(Phase 4) 가 트랜잭션 경계와 함께 소유한다.

## Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.payment`)

**Repository**

- [x] 거래 식별자로 결제를 조회할 수 있다
- [x] 처리 중(`REQUESTED`) 결제 목록을 조회할 수 있다 (폴링 대상)

**외부 PG HTTP 어댑터**

- [x] 결제를 요청하면 거래 식별자와 처리 중 상태를 받는다
- [x] 거래 식별자로 외부 결제 상태를 조회할 수 있다
- [x] 주문 식별자로 외부 결제건을 조회할 수 있다 (식별자 미확보 복구용)
- [x] 외부 응답이 정한 시간을 넘기면 타임아웃으로 종료되고 스레드를 무한 점유하지 않는다
- [x] 외부 통신 실패·타임아웃이 애플리케이션 예외로 변환되어 누수되지 않는다
- ~~승인된 결제의 환불을 외부 PG 에 요청한다~~ — 보류 (pg-simulator 환불 API 없음)

**CircuitBreaker**

- [x] 반복 실패하면 회로가 열려(Open) 이후 호출이 차단된다
- [x] 회로가 열린 동안의 호출은 외부를 호출하지 않고 Fallback 으로 처리된다
- [x] 느린 응답(slow call)이 임계치를 넘으면 실패로 간주되어 차단에 반영된다

**Retry (Nice-To-Have)**

> transient fault 에 한정한 재시도. CircuitBreaker 와 결합하되, 재시도가 이중 결제를 만들지 않아야 한다.

- [x] 일시적 통신 실패는 지정한 횟수까지 재시도된다
- [x] 재시도 대상이 아닌 예외(영구 실패 등)는 재시도 없이 즉시 전파된다
- [x] 재시도 사이에 backoff 대기를 둔다
- [x] 최대 시도를 소진하면 재시도를 멈추고 Fallback 으로 이어진다
- [x] 반복 재시도의 누적 실패가 회로 차단으로 이어진다
- [x] 재시도로 같은 요청이 여러 번 나가도 이중 결제가 발생하지 않는다

## Phase 4 — Application Facade (`com.loopers.application.payment`)

**결제 요청**

- [x] 결제대기 주문에 결제를 요청하면 요청 상태로 생성되고 접수 정보를 반환한다
- [x] 접수되면 외부 거래 식별자가 결제에 기록된다
- [x] 결제 가능하지 않은 주문에 요청하면 `ORDER_NOT_PAYABLE` 예외가 발생한다
- [x] 같은 주문에 동시·중복 요청이 와도 한 번만 결제 진행으로 전이된다
- [x] 타인 소유 주문에 요청하면 `ORDER_FORBIDDEN` 예외가 발생한다
- [x] 외부 호출이 타임아웃·통신 실패·회로 차단이면 요청 상태로 남고 즉시 접수 응답을 반환한다
- [x] 외부 호출 전에 결제를 먼저 영속해, 호출이 실패해도 복구 대상으로 남는다

**정산 (콜백·폴링 공통)**

- [x] 외부 결과가 성공이면 결제 승인 + 주문 결제완료로 전이된다
- [x] 외부 결과가 실패면 결제 실패 + 재고·쿠폰 보상 + 주문 결제실패로 전이된다
- [x] 이미 정산된 결제에 결과를 다시 반영하면 멱등하게 무시된다
- [x] 같은 결과가 콜백·폴링으로 두 번 도착해도 정산은 한 번만 일어난다
- [x] 알 수 없는 거래 식별자의 통지는 정산 없이 무시된다

**상태 복구 (폴링·수동)**

- [x] 처리 중 결제를 복구 트리거하면 외부 상태를 조회해 확정 시 정산한다
- [x] 거래 식별자가 없는 결제는 주문 식별자로 조회해 정산한다 (타임아웃 성공 수렴)
- [x] 외부가 미확정이거나 조회 실패면 상태를 바꾸지 않고 미확정으로 둔다

**취소·환불** — 보류 (pg-simulator 환불 API 없음). 환불 가능 PG 연동 시 재도입.

## Phase 5 — Controller E2E (`com.loopers.interfaces.api.payment`)

**결제 요청 — `POST /api/v1/payments`**

- [x] 결제대기 주문에 결제를 요청하면 200 과 `REQUESTED` 접수 응답을 받는다
- [x] 응답 본문에 카드 번호가 노출되지 않는다
- [x] 카드 번호 형식이 틀리면 400 `INVALID_CARD_NUMBER` 를 받는다
- [x] 지원하지 않는 카드 종류면 400 `UNSUPPORTED_CARD_TYPE` 를 받는다
- [x] 인증 헤더가 없으면 401 `UNAUTHORIZED` 를 받는다
- [x] 존재하지 않는 주문이면 404 `ORDER_NOT_FOUND` 를 받는다
- [x] 타인 소유 주문이면 403 `ORDER_FORBIDDEN` 을 받는다
- [x] 결제 불가 주문이면 409 `ORDER_NOT_PAYABLE` 을 받는다
- [x] 외부 PG 장애·지연에도 스레드 점유 없이 즉시 200(처리 중)을 응답한다

**콜백 — `POST /api/v1/payments/callback`** (회원 인증 없음)

- [x] 성공 콜백을 받으면 200 을 응답하고 주문이 결제완료가 된다
- [x] 실패 콜백을 받으면 200 을 응답하고 주문이 결제실패가 되며 재고·쿠폰이 보상된다
- [x] 같은 콜백을 두 번 받아도 정산은 한 번만 반영된다
- [x] 알 수 없는 거래 식별자 콜백도 200 으로 받고 상태가 변하지 않는다
- [x] 콜백 본문 형식이 틀리면 400 `BAD_REQUEST` 를 받는다

**수동 복구 — `POST /api-admin/v1/payments/{paymentId}/sync`** (LDAP 인증)

- [x] 처리 중 결제를 복구 트리거하면 확정 시 정산되고 갱신된 상태를 응답한다
- [x] 외부가 미확정이면 상태 변화 없이 `settled=false` 로 응답한다
- [x] 존재하지 않는 결제면 404 `PAYMENT_NOT_FOUND` 를 받는다
- [x] LDAP 인증에 실패하면 401 `UNAUTHORIZED` 를 받는다

## 회복 전략 부하 검증 (k6 시나리오)

> `.docs/week6/k6/` 스크립트로 외부 PG 장애를 주입해 회복 전략을 부하 상태에서 검증한다. 도메인 TDD 가 아니라 시나리오 기반 부하 테스트다.

- [ ] **Timeout** — PG 응답 지연 부하에서 결제 요청이 타임아웃 내로 끊기고 스레드가 점유되지 않는다
- [ ] **CircuitBreaker** — PG 반복 실패 부하에서 회로가 열려 외부 호출이 차단되고 응답 지연이 급감한다
- [ ] **Fallback** — 회로가 열린 동안에도 결제 요청 API 가 즉시 처리 중(REQUESTED) 응답을 유지한다
- [ ] **Retry** — 일시 실패를 주입한 부하에서 재시도로 성공률이 회복된다
- [ ] **장애 격리** — PG 장애 주입 중에도 내부 API p99 지연·에러율이 임계 내로 유지된다

## 시니어 파트너 Skill (Nice-To-Have)

> 코드 테스트가 아닌 별도 산출 작업. 외부 연동 설계를 트랜잭션 경계·상태 일관성·실패 시나리오·멱등성 관점으로 분석/질의응답한다 (설계 생성은 하지 않는다).

- [ ] `analyze-external-integration` SKILL 작성

---

## 확장 — 멀티 PG enum 매핑 · 카드/실패원인 저장

> 결제 컨텍스트(카드사·마스킹 카드번호·실패 원인)를 영속하고, PG-중립 도메인과 PG별 와이어 표현 사이에 매핑 계층(ACL)을 둔다.

### 결정 (2026-06-26)

- **카드번호 = 마스킹만 저장** — 끝 4자리(`****-****-****-1451`). 원본 PAN 미보관(PCI 부담 회피). 기존 "미저장" 보안 문구를 "마스킹 저장"으로 갱신.
- **카드사 = 내부 `CardType` 저장**.
- **실패 원인 = `PaymentFailureCause` enum + 원문 사유 병행 저장**.
- **멀티 PG = enum 매핑 ACL 까지만** — 내부 `CardType` ↔ PG 와이어 값, PG raw reason → 내부 `PaymentFailureCause` 매핑을 어댑터가 소유. **PG 라우팅·`PaymentGatewayResolver`·`PgProvider` 선택/저장은 보류**(2번째 PG 도입 시).

### 구조 변경 (Tidy First — 행위 무변경, 별도 커밋)

- [ ] 포트 `PaymentRequestCommand.cardType` 를 String → 도메인 `CardType` enum 으로 (와이어 매핑 책임을 어댑터로 이전)
- [ ] 어댑터가 내부 `CardType` → pg-simulator 와이어 값으로 **명시 매핑**한다 (`cardType.name` 직접 노출 제거 — 같은 값, 매핑만 명시화)

### Phase 1 — 도메인 모델 (`com.loopers.domain.payment`)

- [ ] 카드번호 VO 가 마스킹 표현(끝 4자리)을 제공한다 — 원본은 외부 전달 전용
- [ ] `PaymentFailureCause` 가 외부 실패를 한도초과/잘못된카드/그외로 분류한다
- [ ] 결제에 카드사·마스킹 카드번호를 보관할 수 있다
- [ ] 결제가 실패로 확정될 때 실패 원인(enum)과 원문 사유를 함께 보관한다

### Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.payment`)

- [ ] 어댑터가 PG 의 실패 사유(raw)를 내부 `PaymentFailureCause` 로 매핑한다 (포트 `PgTransaction` 이 `failureCause` 를 싣는다)
- [ ] `PaymentEntity` 가 카드사·마스킹 카드번호·실패 원인 컬럼을 영속한다 (DB 마이그레이션)

### Phase 4 — Application Facade (`com.loopers.application.payment`)

- [ ] 결제 요청 시 카드사·마스킹 카드번호가 결제에 저장된다
- [ ] 정산 실패 시 실패 원인(enum)·원문이 결제에 저장된다

### Phase 5 — 조회/E2E

- [ ] 카드 마스킹·실패 원인이 조회 응답(회원/어드민)에 노출되되, **원본 카드번호는 어떤 응답·로그에도 없다**

### 문서

- [ ] requirements §1(카드 정보)·§5(보안), api-spec §0.4, logical-model 에 "마스킹 저장" 정책 반영 (기존 "미저장" 번복 명시)

### 보류 (이번 미구현)

- PG 라우팅 전략 · `PaymentGatewayResolver` · `PgProvider` 선택/저장 — enum 매핑 ACL 만 우선 도입하고, 실제 라우팅은 2번째 PG 도입 시 별도 ADR 로 다룬다.

---

## 진행 로그

- **2026-06-23** — 초기 케이스 도출. `Payment.accept`·결제 조회·PG 어댑터(`request`/`getTransaction`/`getByOrder`) 구현.
- **2026-06-24** — `PaymentFacade`(pay/settle/sync) 단일 진입점 확립. 컨트롤러·콜백·수동복구 E2E. 동기 잔재(`PaymentSettler`·`PaymentResult`·자동 트리거·refund) 제거.
- **2026-06-25** — Timeout·CircuitBreaker·Fallback 적용. 외부 호출 전 선영속으로 복구 보장.
- **2026-06-25** — Retry(Nice-To-Have) 완료. PG 어댑터에 resilience4j-retry 적용(Retry 바깥·CircuitBreaker 안쪽). 재시도/비재시도/backoff/소진→Fallback/누적→차단/이중 결제 없음 6케이스 검증. k6 회복 전략 부하 검증 시나리오를 plan 에 추가.
- **2026-06-25** — (구조) 전송 추상화 `PaymentApiClient` 분리. `RestClientPaymentApiClient` 가 HTTP 호출·와이어 DTO·예외 변환을, `PgSimulatorPaymentGateway` 는 회복 전략(CB·Retry)만 담당 — 전송 라이브러리 교체가 구현체 교체로 끝나게 됨. 행위 무변경.
- **2026-06-26** — (행위) Retry·CircuitBreaker 적용 순서를 `CircuitBreaker(Retry(call))` 로 swap — Retry-바깥이 half-open permit 을 시도 수만큼 잡아먹던 함정 해소. half-open permit 3 명시, 재시도 묶음=CB 1 이벤트. `accumulatedRetryFailuresOpenCircuit` → `retriesCountAsSingleCircuitEvent` 재작성. 결정 기록 [resilience-decisions.md](./resilience-decisions.md) ADR-001.
- **2026-06-26** — (튜닝) CB 파라미터 7종 점검 — `slowCallDurationThreshold` 2s→1s(read timeout 과 겹침 해소), `slowCallRateThreshold` 100%→50%, `recordExceptions(PaymentGatewayException)` 한정. automaticTransition·permitted(3)·window(10)·failAfterMaxAttempts 는 분석 후 유지. 결정 기록 [resilience-decisions.md](./resilience-decisions.md) ADR-002.
- **2026-06-26** — (행위) 4xx 를 재시도/차단에서 제외 — 새 예외 타입 없이 어댑터가 4xx(`HttpClientErrorException`)를 감싸지 않고, Retry 에 `ignoreExceptions(HttpClientErrorException)` 명시(화이트리스트가 이미 제외·의도 표면화). CB 무변경. Facade 가 `PaymentGatewayException` 만 잡아 4xx 는 fail-fast(폴링 지옥 회피). Retry 고정→지수+지터 backoff. 타임아웃(connect 1s/read 2s/slow 1s)은 접수(100~500ms) 커버로 적정 판단해 유지. `doesNotRetryClientError` 테스트. 결정 기록 ADR-003(안 B 채택, 안 A=신규타입 기각).
