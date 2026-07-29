---
name: analyze-query
description: "Spring @Transactional · JPA · QueryDSL 코드를 트랜잭션 범위 / 영속성 컨텍스트 / 쿼리 실행 시점 / 멱등성 관점으로 분석한다. 정답을 강요하지 않고 현재 구조의 의도와 trade-off 를 드러내 개선 지점을 선택적으로 판단하도록 돕는다. 사용: /analyze-query <분석 대상: 클래스/메서드/유즈케이스/경로>"
argument-hint: "<분석 대상: 클래스/메서드/유즈케이스/경로>"
---

# /analyze-query — 트랜잭션 · 영속성 · 쿼리 · 멱등성 분석

> 인자: `$ARGUMENTS` — 분석 대상 (예: `OrderFacade.placeOrder`, `order`, `interfaces/api/order`).
> 단순한 정답 제시가 아니라, 현재 구조의 **의도와 trade-off** 를 드러내고 개선 가능 지점을 **선택적으로** 판단하도록 돕는 것이 목적이다.
> 본문 톤은 한국어 `~다` 종결을 유지한다.

## 0) 입력 검증
- `$ARGUMENTS` 가 비어 있으면 중단하고 분석 대상(클래스·메서드·유즈케이스·경로)을 묻는다.
- 대상이 모호하면 `Controller → Facade/Service → Repository` 흐름의 진입점을 먼저 식별한 뒤 분석을 시작한다.

## 📌 Analysis Scope
아래 대상에 대해 분석한다.
- `@Transactional` 이 선언된 클래스 / 메서드
- `application.Facade` / `domain` Service / Application Layer 코드
- JPA Entity, Repository, RepositoryImpl, QueryDSL 사용 코드
- **하나의 유즈케이스(요청 흐름) 단위**

> 컨트롤러 → 파사드/서비스 → 레포지토리 **전체 흐름**을 기준으로 분석하며, 특정 메서드만 떼어내어 판단하지 않는다.
> 본 저장소는 트랜잭션 경계와 조율 로직을 `application.Facade` 가 단일 소유한다 (CLAUDE.md). 분석 시 이 의도를 기준선으로 삼는다.

## 🔍 Analysis Checklist

### 1. Transaction Boundary 분석
다음을 순서대로 확인한다.
- 트랜잭션 **시작 지점**은 어디인가? (Facade / Service / 그 외 계층)
- 트랜잭션이 실제로 필요한 작업은 무엇인가? — 상태 변경(쓰기) vs 단순 조회
- 트랜잭션 내부에서 수행되는 작업을 나열한다.
  - 외부 API 호출 (`application.<usecase>.port` 의 outbound gateway)
  - 복잡한 조회 (QueryDSL)
  - 반복문 기반 처리
- **전파(propagation) / 격리 수준 / readOnly** 가 명시되어 있는가? 기본값(`REQUIRED`, DB 기본 격리)에 암묵 의존하고 있지는 않은가?

**출력 예시**

```markdown
- 현재 트랜잭션 범위:
OrderFacade.placeOrder()
  ├─ 유저 검증
  ├─ 상품 조회
  ├─ 주문 생성
  ├─ 결제 요청        ← 외부 호출 (port)
  └─ 재고 차감

- 트랜잭션이 필요한 핵심 작업:
  - 주문 생성
  - 재고 차감
```

### 2. 불필요하게 큰 트랜잭션 식별
아래 패턴이 존재하는지 점검한다.
- Controller(`interfaces.api`) 에서 `@Transactional` 이 사용됨 — 경계가 계층을 넘어옴
- 읽기 전용 로직이 쓰기 트랜잭션에 포함됨
- **외부 시스템 호출이 트랜잭션 내부에 포함됨** — 응답 지연이 곧 락 점유 시간이 된다
- 트랜잭션 내부에서 대량 조회 / 복잡한 QueryDSL 실행
- 상태 변경 이후에도 트랜잭션이 길게 유지됨 (커밋 지연)
- 락 점유 구간이 긴가? (`SELECT ... FOR UPDATE`, 낙관/비관 락 범위)

**문제 후보 예시**
- 결제 API 호출이 트랜잭션 내부에 포함되어 있음 → 외부 지연 동안 DB 커넥션·락 점유
- 주문 생성 이후 추천 상품 조회 로직까지 동일 트랜잭션에 포함됨

### 3. JPA / 영속성 컨텍스트 관점 분석
다음을 중심으로 분석한다.
- Entity 변경이 **언제 flush** 되는지 (커밋 시점 / JPQL·QueryDSL 실행 직전 auto-flush)
- 조회용 Entity 가 **변경 감지(dirty checking)** 대상이 되는지 — 의도치 않은 UPDATE 가능성
- 지연 로딩(Lazy)으로 인해 트랜잭션 후반·뷰 렌더링 시점에 쿼리가 발생할 가능성 (N+1, `LazyInitializationException`)
- `@Transactional(readOnly = true)` 미적용 여부 — 스냅샷 보관 비용·flush 모드
- fetch join / `@EntityGraph` 로 N+1 을 차단하고 있는지

**체크리스트 예시**

```markdown
- 단순 조회인데 Entity 반환 후 변경 가능성 존재?
- DTO Projection 대신 Entity 조회 사용 여부
- QueryDSL 조회 결과가 영속성 컨텍스트에 포함되는지 (Entity vs Projection)
- readOnly 트랜잭션에서 쓰기가 시도되지는 않는지
```

### 4. 멱등성(Idempotency) · 재시도 안전성 분석
외부 호출·재시도·중복 요청 관점에서 점검한다 (CLAUDE.md: "외부 호출은 타임아웃·멱등성").
- **재시도 안전성**: 트랜잭션이 부분 커밋 후 실패하면 어떤 상태가 남는가? 같은 요청을 다시 보내면 안전한가?
- **외부 호출 멱등키**: 결제·발송 등 outbound port 호출에 멱등키(idempotency key)·요청 식별자가 전달되는가? 재시도 시 중복 부수효과가 없는가?
- **중복 요청 방어**: 동일 유즈케이스 동시/중복 호출 시 DB unique 제약·낙관적 락·상태 전이 검증으로 막히는가?
- **부수효과 위치**: 외부 호출·이벤트 발행이 커밋 **전**에 일어나 롤백돼도 부수효과만 남는 구조는 아닌가? (트랜잭션 내부 발행 vs `@TransactionalEventListener(AFTER_COMMIT)`)
- **soft delete / 멱등 처리**: 이미 처리된 요청을 다시 받으면 동일 결과를 반환하는가, 아니면 예외/중복 생성이 발생하는가? (user_design_preferences: soft delete 멱등)

**체크리스트 예시**

```markdown
- 외부 호출 실패 → 재시도 시 중복 결제/중복 차감 위험?
- 멱등키가 요청~외부호출까지 일관되게 전파되는가?
- 커밋 전에 발행된 이벤트가 롤백 시에도 소비되지는 않는가?
- 중복 요청을 unique 제약 / 상태 검증으로 차단하는가?
```

### 5. Improvement Proposal (선택적 제안)
개선안은 **강제하지 않고 선택지로** 제시한다. 각 개선안에는 trade-off·고려사항을 함께 적는다.
- **트랜잭션 분리**: 조회 → 쓰기 분리 / Facade 는 orchestration, Service 는 최소 트랜잭션
- `@Transactional(readOnly = true)` 적용 (조회 경로)
- **DTO Projection** (읽기 전용 모델) 도입 — 영속성 컨텍스트 오염·dirty checking 회피
- **외부 호출 / 이벤트 발행을 트랜잭션 외부로 이동** — 커밋 후 발행, 멱등키 기반 재시도
- 멱등성 보강: 멱등키 저장·조회, unique 제약, 상태 전이 기반 중복 차단
- Application Service / Domain Service 책임 재조정 (조율은 Facade, 규칙은 domain)

**개선안 예시**

```markdown
[개선안 1] 결제를 트랜잭션 경계 밖으로
- 주문 생성과 결제 요청을 분리
- 주문 생성까지만 트랜잭션 유지 (커밋)
- 결제 요청은 트랜잭션 종료 후 수행

[고려 사항]
- 결제 실패 시 주문 상태 관리 필요 (PENDING → FAILED 전이)
- 보상 트랜잭션 또는 상태 전이 설계 필요
- 결제 호출에 멱등키 부여해 재시도 시 중복 결제 차단
```

## 출력 형식
1. **현재 구조 요약** — 유즈케이스 흐름과 트랜잭션 범위 (트리 형태)
2. **관점별 관찰** — §1~§4 체크리스트 결과 (문제 단정이 아닌 관찰·근거)
3. **개선 선택지** — §5, 각 항목에 trade-off / 고려사항 병기
4. 단정적 "정답" 으로 결론짓지 않는다. 현재 구조의 **의도가 합리적이면 그렇다고 명시**하고, 개선은 어디까지나 선택지로 남긴다.

---

이제 `$ARGUMENTS` 대상의 `Controller → Facade/Service → Repository` 흐름을 읽고, 위 체크리스트(§1~§5) 순서로 분석하라.
