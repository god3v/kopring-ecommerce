# order 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md (v0.5)](./requirements.md) 와 [api-spec.md](./api-spec.md) 의 UC-1 ~ UC-5 를 포괄한다.
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `orders` · `order_lines` · `coupons` · `user_coupons`.
> 회원 인증은 `loginId` 헤더 → `UserRepository.findByLoginId`. 어드민 인증(LDAP)은 interfaces 레이어 책임.

---

## 0. 스코프 / 결정 (v0.4 — 설계 리뷰 반영)

### 사용자 결정 (2026-06-10, 코드 리뷰 후)
- **Q1 쿠폰 연동 = A (OrderFacade 직접 조율)**: `OrderFacade` 가 `UserCouponRepository`·`CouponRepository` + 도메인 객체(`UserCoupon.use`·`Coupon.calculateDiscount`·`Coupon.isExpired`) 를 **자신의 트랜잭션 안에서 직접 조율**한다. 현행 `CouponFacade.applyCoupon` 호출(B) 은 제거한다. requirements §5 를 A 에 맞춰 개정 완료(v0.4).
- **Q2 식별자 명명 = 전면 `userCouponId` 개명**: 와이어(JSON) 필드 포함 전부 `couponId` → `userCouponId`. (week4 원 스펙의 `couponId` 와 의도적으로 다름 — api-spec §0.4 에 명시.)
- **Q3 결제 = 최소 PaymentGateway 지금 도입(올바른 형태)**: `application.order.port.PaymentGateway`(outbound) + `PaymentResult` VO + `infrastructure.order` 항상 성공 어댑터. **주문 저장 트랜잭션 커밋 이후** 호출(트랜잭션 안 호출 아님). 흐름: `tx1(재고+쿠폰+주문 PAYMENT_PENDING) → 커밋 → PG(항상 성공) → tx2(markPaid → PAID)`.
- **락 전략 = TBD**: 발급 쿠폰 단일 사용은 Coupon 이 비관 락(`findByIdForUpdate`) 제공(이미 구현). 재고 차감 락(비관/낙관)은 미정(§5).

### 베이스라인 (현재 코드 — 검증 완료)
- **coupon 도메인**: domain(9) · application(`CouponFacade`) · infrastructure(6) 전부 구현됨. `CouponFacade.applyCoupon(userId, userCouponId, orderAmount): Long` 은 `findByIdForUpdate` 비관 락 + 소유/사용/만료 검증 + `calculateDiscount` + `use` + save. `UserCouponRepository.findByIdForUpdate`, `CouponRepository.findByIdIncludingDeleted` 존재.
- **order 도메인**: `Order`(couponId·discountAmount·originalAmount·totalAmount·markPaid/markPaymentFailed·applyCoupon(userCouponId, discountAmount)), `OrderStatus`(PENDING/PAID/FAILED), `OrderService.createOrder`(재고 차감+라인 스냅샷), `OrderFacade`(placeOrder 가 **CouponFacade.applyCoupon 호출(B)**, PG 없음, 주문을 PENDING 으로 저장 후 markPaid 미호출 → PENDING 잔존 드리프트), 5 메서드. infra(OrderEntity 등). 테스트(OrderFacadeTest·OrderTest·OrderRepositoryImplIntegrationTest).
- **interfaces.api.order**: 없음(HTTP 엔드포인트 미존재).

### 갭 (v0.4 에서 닫을 것)
- B(CouponFacade 호출) → **A(직접 조율)**. `CouponFacade.applyCoupon` + 그 테스트 제거, 조율을 OrderFacade 로 이주.
- 와이어/내부 `couponId` → **`userCouponId`** 전면 개명.
- PG 부재 + PENDING 잔존 → **PaymentGateway(커밋 후) + PAID 전이**.
- 3금액(originalAmount/discountAmount/totalAmount) 노출 명명 확정 (현재 originalAmount/totalAmount 게터).
- interfaces 부재 → 컨트롤러·ApiSpec·DTO·E2E.

---

## 1. Phase 1 — 구조 변경 (Tidy First, 행위 무변경) — **별도 커밋**

> 구조 변경과 행위 변경은 같은 커밋에 섞지 않는다 (CLAUDE.md). 먼저 개명만, 빌드/테스트 그린 유지.

### 1.1 couponId → userCouponId 전면 개명 ✅ (2026-06-10)
- [x] `PlaceOrderCommand.couponId` → `userCouponId`
- [x] `Order.couponId` 필드·생성자·`create()` 파라미터 → `userCouponId` (`applyCoupon` 파라미터는 이미 userCouponId)
- [x] `OrderResult.couponId` / `AdminOrderResult.couponId` → `userCouponId` (+ from/of 매핑)
- [x] `OrderEntity.couponId` 필드 → `userCouponId`, `@Column(name = "user_coupon_id")` (운영 데이터 없음 — 컬럼 정렬)
- [x] 테스트(`OrderTest`·`OrderFacadeTest`·`OrderRepositoryImplIntegrationTest`·`OrderCouponIntegrationTest`)의 `couponId` 접근부 갱신 — `UserCoupon.issue(couponId=…)`(쿠폰 도메인 파라미터)는 보존
- [x] 빌드·전체 테스트 그린 확인 (컴파일·단위·통합·ktlint 모두 통과, 행위 변화 없음)

---

## 2. Phase 2 — 쿠폰 직접 조율 전환 (A) — **별도 커밋 (행위)**

> mock 기반 단위 테스트. CouponFacade 호출(B) → 도메인/리포 직접 조율(A).

### 2.1 OrderFacade 의존성 교체 ✅ (2026-06-10)
- [x] 생성자에서 `CouponFacade` 제거, `UserCouponRepository`·`CouponRepository` 주입
- [x] `placeOrder` 안에 쿠폰 조율을 **인라인**(private 메서드 회피): `findByIdForUpdate` → 소유자·`isUsed()`·`isExpired(now)` 검증 → `calculateDiscount(originalAmount)` → `use(now)` → `save` → `order.applyCoupon(...)`
- [x] `CouponRepository.findByIdIncludingDeleted`(소프트삭제 템플릿 사용 허용) 분기·비관 락(`findByIdForUpdate`)·`CouponErrorType` 예외 타입 보존
- [x] 전 과정이 `placeOrder` 단일 `@Transactional` 안 → 후속 실패 시 쿠폰 소진까지 롤백 (OrderCouponIntegrationTest 로 확인)

### 2.2 테스트 이주 ✅ (2026-06-10)
- [x] `OrderFacadeTest` 의 `couponFacade` 목킹을 `UserCouponRepository`·`CouponRepository` 목킹 + `CouponFixture` 도메인 실객체로 교체 (쿠폰 적용·소유자 불일치·이미 사용·만료 시나리오)
- [x] 최소금액 미달 등 e2e 시나리오는 `OrderCouponIntegrationTest`(동시성 단일 소진 포함) 가 커버
- [x] `CouponFacade.applyCoupon` + `CouponFacadeTest` 의 applyCoupon 케이스 제거 (호출처 없음 — order 가 직접 조율)
- [x] 컴파일·OrderFacadeTest·OrderCouponIntegrationTest·CouponFacadeTest·도메인·ktlint 모두 그린

---

## 3. Phase 3 — 3금액 노출 정합 — ✅ (2026-06-10)

> **명명 결정**: 코드 어휘로 통일. 도메인 getter `originalAmount`(적용 전)·`discountAmount`·`totalAmount`(최종) 를 **그대로 유지**(gross/payable 로 개명하지 않음). v0.4 문서(api-spec·requirements §1)에 임의로 넣었던 `grossAmount`/`payableAmount` 는 `originalAmount`/`totalAmount` 로 정정.

### 3.1 Order 금액 노출
- [x] 도메인 getter `originalAmount`/`totalAmount` 유지 — 개명 없음
- [x] `OrderResult`/`AdminOrderResult` 가 3금액(`originalAmount`·`discountAmount`·`totalAmount`) 모두 노출 (`originalAmount` 신규 추가)
- [x] 3금액은 라인 스냅샷 + 저장된 `discountAmount` 에서 파생 — **OrderEntity 에 별도 gross/payable 컬럼 추가하지 않음**(중복 비정규화 회피, 불변 보장은 라인+할인 스냅샷으로 충족)
- [x] api-spec §0.6·예시, requirements §1 유비쿼터스 언어를 코드 어휘로 정정
- [x] OrderCouponIntegrationTest 에 `originalAmount` 단언 추가, 컴파일·order 테스트·ktlint 그린

---

## 4. Phase 4 — PaymentGateway 도입 (커밋 후 결제, 이벤트 방식) — ✅ (2026-06-10)

> **구조 결정 = 도메인 이벤트 + AFTER_COMMIT 리스너 + PaymentFacade 오케스트레이션.** `placeOrder`(tx1) 가 재고·쿠폰·주문 PENDING 저장 후 `OrderPlacedEvent` 발행 → 커밋 → `@TransactionalEventListener(AFTER_COMMIT)` 리스너가 `PaymentFacade.pay(orderId)`(`@Transactional(REQUIRES_NEW)`) 를 트리거(tx2). 트랜잭션 경계·결제 조율은 `PaymentFacade` 가 소유하고 리스너는 위임만 한다. 외부 호출이 주문 트랜잭션 밖.
> **생성 응답은 `PAYMENT_PENDING`** (응답값이 리스너 실행 전에 조립됨 — 리스너가 응답을 주지 못하는 구조). 운영 게이트웨이가 항상 성공(`AlwaysSuccessPaymentGateway`)이라 정상 경로에서는 리스너가 커밋 직후 동기 실행되어 DB 는 즉시 `PAID`, 이후 조회는 `PAID`. 결제 실패 시에는 보상 후 `PAYMENT_FAILED` 로 마감되어 이후 조회에 그 상태로 드러난다(§4.3).

### 4.1 port / VO / adapter
- [x] `application.order.port.PaymentGateway` — `charge(orderId, amount): PaymentResult`
- [x] `PaymentResult(transactionId, resultCode, success)` VO (애그리거트 아님)
- [x] `infrastructure.order.AlwaysSuccessPaymentGateway` — `tx-$orderId` + `APPROVED`

### 4.2 이벤트 흐름
- [x] `application.order.OrderPlacedEvent(orderId)` + `OrderFacade` 가 새 주문 저장 후 `ApplicationEventPublisher` 로 발행 (멱등 재요청·검증 실패 시 미발행)
- [x] `application.order.OrderPaymentEventListener` — AFTER_COMMIT 시점만 책임지는 **얇은 트리거**(`paymentFacade.pay(event.orderId)` 위임, 비즈니스 로직 0). 결제 오케스트레이션(로드→PG charge→성공 `markPaid`/실패 보상+`markPaymentFailed`→save)은 `application.order.PaymentFacade.pay(orderId)`(`@Transactional(REQUIRES_NEW)`)로 이주. (REQUIRES_NEW 인 이유: AFTER_COMMIT 콜백은 이미 커밋된 원 트랜잭션이 바인딩돼 있어 평범한 `@Transactional` 은 조인만 하고 커밋되지 않는다.) 운영 게이트웨이는 여전히 `AlwaysSuccessPaymentGateway` 라 정상 실행에서는 실패 분기를 타지 않는다.
- [x] 정상(통합): `placeOrder` 응답 `PAYMENT_PENDING`, 커밋 후 리스너가 주문을 `PAID` + `paymentTransactionId` 박음 (OrderCouponIntegrationTest 검증)
- [x] 멱등(단위): 재요청 시 기존 주문 반환, 이벤트 미발행(`verify(exactly=0)`)
- [x] 컴파일·OrderFacadeTest·OrderCouponIntegrationTest(동시성 포함)·ktlint 그린

### 4.3 결제 실패 보상 + PaymentFacade 추출 ✅ (2026-06-11)

> 결제 오케스트레이션을 리스너에서 `application.order.PaymentFacade.pay(orderId)`(`@Transactional(REQUIRES_NEW)`) 로 추출하고, 실패 분기 보상(차감 재고 원복 + 소진 쿠폰 복원 + `PAYMENT_FAILED` 전이)을 구현했다. 리스너는 트리거만 하는 얇은 위임으로 슬림화했다. `PAYMENT_FAILED` 는 이제 도달 가능한 상태다 — 단 운영 게이트웨이는 여전히 `AlwaysSuccessPaymentGateway`(항상 성공)라 정상 실행에서는 실패가 나지 않고, 실패 경로·보상은 테스트로만 커버된다. **실제 외부 PG 연동(mock 교체)** 만 향후 과제로 남는다.

- [x] `PaymentFacade.pay(orderId)` 추출 — 로드 → `order.status != PAYMENT_PENDING` 이면 early-return(멱등 가드: 중복 트리거 시 이중 청구·이중 보상 방지) → PG `charge` → 성공 `markPaid` / 실패 `compensate` + `markPaymentFailed` → save. 리스너는 `paymentFacade.pay` 위임만 하는 얇은 트리거로 슬림화
- [x] 실패 보상(같은 REQUIRES_NEW 트랜잭션): 주문 라인별 `Product.restoreStock(quantity)` → `saveAll`, 소진 쿠폰 `UserCoupon.cancelUse()`(USED→AVAILABLE, usedAt=null; 사용 전이면 멱등 no-op) → save
- [x] `UserCoupon.cancelUse()` 도메인 메서드 추가(결제 실패 보상용, 멱등)
- [x] `PaymentFacadeTest`(단위) — 성공 markPaid / 실패 보상·전이 / 멱등 가드(PENDING 아니면 no-op)
- [x] `OrderPaymentFailureIntegrationTest` — `@MockkBean` 으로 실패 게이트웨이 주입, 실제 DB 로 재고(10 원복)·쿠폰(AVAILABLE 원복)·주문상태(`PAYMENT_FAILED`) 보상 검증
- [x] 컴파일·PaymentFacadeTest·OrderPaymentFailureIntegrationTest·기존 order 테스트·ktlint 전체 그린

---

## 5. Phase 5 — interfaces (행위 — E2E)

> 산출물: `OrderV1Controller`(`/api/v1/orders`) · `OrderV1AdminController`(`/api-admin/v1/orders`) · `*ApiSpec` · DTO. `@RequireAuth`+`@LoginUser`(회원, `AuthUser.loginId` 를 Facade 에 전달), `AdminAuthInterceptor`(어드민, 경로 강제). 와이어 필드 `userCouponId`, 멱등 키 `Idempotency-Key` 헤더(미존재 시 빈 문자열 → 도메인이 `IDEMPOTENCY_KEY_BLANK`).
>
> **페이징 = PageResult (coupon 일관) ✅ 사전 완료 (2026-06-10).** `OrderRepository.findAllByUserIdInPeriod`/`findAllForAdmin` 과 `OrderFacade.getMyOrders`/`getOrdersForAdmin` 을 `List` → `PageResult` 로 변경(`OrderJpaRepository` 가 `Page` 반환 + 기간 쿼리에 `countQuery`, `Page.toPageResult()` 확장). 목록 응답 DTO 는 coupon 처럼 `content`/`page`/`size`/`totalElements`/`totalPages`. (OrderFacadeTest·OrderRepositoryImplIntegrationTest 갱신, 그린.)

### 회원 채널 ✅ (2026-06-10) — `OrderV1Controller`·`OrderV1ApiSpec`·`OrderV1Dto`·`OrderV1ApiE2ETest`
- [x] UC-1 `POST /api/v1/orders` — 정상(쿠폰 적용→PENDING→커밋후 PAID 전이/미적용) / IDEMPOTENCY_KEY_BLANK / EMPTY_LINES / ALREADY_USED_COUPON / 멱등 수렴 / UNAUTHORIZED (E2E)
- [x] UC-2 `GET /api/v1/orders` — 목록(PageResult)·INVALID_DATE_RANGE (getMyOrders 에 기간 검증 가드 추가 + 단위 테스트)
- [x] UC-3 `GET /api/v1/orders/{orderId}` — 본인 상세(PAID) / ORDER_NOT_FOUND / ORDER_FORBIDDEN(타인)
### 어드민 채널 ✅ (2026-06-10) — `OrderV1AdminController`·`OrderV1AdminApiSpec`·`OrderV1AdminApiE2ETest` (`X-Loopers-Ldap`, `AdminAuthInterceptor` 경로 커버)
- [x] UC-4 `GET /api-admin/v1/orders` — 운영 메타(마스킹 표시명·PAID·결제 메타) 포함 PageResult / `X-Loopers-Ldap` 누락 → `401`
- [x] UC-5 `GET /api-admin/v1/orders/{orderId}` — 어느 회원 주문이든 조회 / `ORDER_NOT_FOUND`

---

## 6. Phase 6 — 동시성 (비관 락 보강)

> **락 전략 결정 (2026-06-12): 비관 락 채택.** 저장소는 재고 차감(`products FOR UPDATE`)·쿠폰 사용(`userCoupon FOR UPDATE`) 을 비관 락으로 일관되게 다스린다. 결제 보상·재결제 트리거의 잔여 경합도 같은 언어(비관 락)로 닫는다. `@Version`(낙관) 은 Order 의 *모든* 쓰기 경로에 낙관 실패(`OptimisticLockException`) 시맨틱을 전파시켜(비관 락 경로에선 검사만 무해히 따라붙지만, 무락 경로가 재시도 의무를 떠안음) 채택하지 않는다.
> **락 순서(데드락 안전):** 모든 경로가 products 를 id 오름차순(`findAllByIdsForUpdate ... ORDER BY p.id`)으로 잠근다. pay() 의 order 행 락은 사이클에 참여하지 않는다(placeOrder 는 order 를 신규 insert 라 기존 행을 잠그지 않음 → order→products 단방향). 따라서 대기 그래프에 사이클이 없다.

### 6.0 기존 동시성 가드
- [x] 동일 발급 쿠폰 N 동시 주문 → 정확히 1건 성공, 나머지 `ALREADY_USED_COUPON` (`OrderCouponIntegrationTest.concurrentOrdersConsumeCouponOnce` — Coupon 비관 락 직렬화)
- [x] 동일 상품 재고 K 에 N 동시 주문 → 정확히 차감·전건 성공 (forward deduct 락 — §6.3 `[5]` 가 K=N 경계로 커버)

### 6.1 재고 복원 비관 락 (보상 ↔ 신규 차감 경합) — 행위 커밋

> **문제:** `PaymentFacade.compensate` 가 `findAllByIds`(무락) 로 읽고 `restoreStock` 후 `saveAll` → read-modify-write. 스냅샷 기준 계산값을 덮어써 동시 차감을 잃는다(lost update → 오버셀 위험). forward 차감은 비관 락인데 보상만 무락 — 비대칭.
> **해법:** compensate 도 기존 `findAllByIdsForUpdate`(ORDER BY id, 이미 존재) 로 잠가 차감과 동일하게 직렬화한다. 신규 리포 메서드 불필요.

- [x] (Red·단위) `PaymentFacadeTest` OnFailure 2건의 stub 을 `findAllByIds` → `findAllByIdsForUpdate` 로 교체 + `verify` → MockKException 으로 Red 확인 (2026-06-12)
- [x] (Green) `compensate` 의 `findAllByIds` → `findAllByIdsForUpdate` (id ASC 락으로 차감과 동일 직렬화)
- [x] (행위 가드·통합) `OrderConcurrencyIntegrationTest` `[7]` — N 동시 결제 실패 주문(각 1 차감 후 보상 +1) → 최종 재고 보존·전건 PAYMENT_FAILED (§6.3 으로 이관)
- [x] `PaymentFacadeTest` · `OrderPaymentFailureIntegrationTest` 그린 (ktlint 최종 일괄 확인)

### 6.2 결제 트리거 이후 주문 비관 락 (동시 pay 직렬화) — 구조 + 행위 커밋

> **문제:** `PaymentFacade.pay` 가 `findById`(무락) + `status != PAYMENT_PENDING` 가드만 가져, 동시 `pay()` 둘이 모두 PENDING 을 읽고 이중 청구·이중 보상할 수 있다. 현재는 트리거가 1회뿐이라 잠재 위험이나, 재시도/스윕 도입 시 활성 결함이 된다.
> **해법:** pay 진입 시 `findByIdForUpdate` 로 주문 행을 잠가 상태 전이(PENDING→PAID/FAILED)를 원자화한다. 늦은 호출은 커밋된 비-PENDING 을 읽고 no-op.

- [x] (구조 커밋·행위 무변경) `OrderRepository.findByIdForUpdate` + `OrderJpaRepository`(@Lock PESSIMISTIC_WRITE) + `OrderRepositoryImpl` 추가 — 호출처 없음, 기존 `PaymentFacadeTest` 그대로 그린으로 중립 확인 (2026-06-12)
- [x] (Red·단위) `PaymentFacadeTest` 의 `orderRepository.findById` 5개 stub → `findByIdForUpdate` + 성공 케이스 `verify` → 5건 MockKException Red
- [x] (Green) `pay` 의 `findById` → `findByIdForUpdate`
- [x] (행위 가드·통합) `PaymentConcurrencyIntegrationTest.concurrentPayChargesExactlyOnce` — 단일 PENDING 주문에 4 동시 `pay()`(charge 150ms 지연 mock) → `charge` 정확히 1회·주문 PAID
- [x] `PaymentFacadeTest` · 신규 통합 그린 (ktlint 최종 일괄 확인)

### 6.3 동시성 통합 스펙 8종 + unlike 게이팅 버그 수정 (2026-06-12)

> **커넥션 풀 주의:** test 프로파일 풀은 10([jpa.yml](../../../modules/jpa/src/main/resources/jpa.yml)). 비관 락 대기 스레드가 커넥션을 점유하고, `pay()` 가 AFTER_COMMIT 안 REQUIRES_NEW 라 결제 구간에서 스레드당 2커넥션을 쓴다. 동시성 스펙은 `@SpringBootTest(properties=[maximum-pool-size=32, minimum-idle=10])` 로 풀을 키워 스레드 8~16 을 돌린다(CPU 무관, 풀 한계 회피).

- [x] `LikeConcurrencyIntegrationTest` — [1] 다수 사용자 동시 like → 카운트 = 인원수 / [2] 다수 사용자 동시 unlike → 0 / [3] 동일 사용자 동시 like → +1 / [4] 동일 사용자 동시 unlike → -1
- [x] `OrderConcurrencyIntegrationTest` — [5] 다수 사용자 동시 주문(성공) → 재고 정확 차감·전건 PAID / [6] 동일 사용자 동일 쿠폰 동시 주문 → 1건만 성공·나머지 ALREADY_USED·쿠폰 1회 소진 / [7] 다수 사용자 동시 주문 결제 전부 실패 → 재고 전량 원복·전건 PAYMENT_FAILED / [8] 동일 사용자 동일 쿠폰 동시 주문 결제 실패 → 소진 쿠폰 AVAILABLE 로 롤백
- [x] **버그 수정**: unlike 감소 게이트(`if delete > 0`) 가 동시성 하에서 무력화돼 있었다. `LikeJpaRepository.deleteByUserIdAndProductId` 가 **파생(derived) delete** 라 "조회 후 제거" 로 동작해 반환값이 *조회 행 수*(동시 요청은 모두 1)였다 → 모두 게이트 통과·중복 감소([4] 재현, 5→0). **벌크 `@Modifying @Query` DELETE + `Int` 반환**으로 교체해 실제 삭제 행 수(패자 0)를 돌려주게 했다. (`@Modifying` 은 `Long` 반환 시 0 으로 매핑되므로 `Int` 필수.)
- [x] §6.0 의 "재고 K 에 N 동시 주문 → 정확히 K 성공" 은 [5] 가 K=N 경계로 커버

---

## 7. Phase 7 — 문서 동기화

- [x] requirements v0.4 — A 조율 주체 명시, userCouponId 전면, PG 커밋 후 2단계 (2026-06-10)
- [x] api-spec — userCouponId 와이어, 상태/결제 메타, PG 부록 (2026-06-10)
- [x] plan v0.4 — A/전면개명/PG + Tidy-First 순서 (2026-06-10)
- [ ] 구현 후 코드 ↔ api-spec 정합 재확인, ERD 3금액·user_coupon_id 반영
- [x] ERD `payments`/`orders` 코드 동기화 (2026-06-12) — 상태명 APPROVED/CANCELED, `canceled_at` 추가, `method` 제거, 결제 정합성 매핑·요청-전-영속 노트 반영

---

## 8. Phase 8 — Payment 애그리거트 (결제 생애주기 분리) — ✅ (2026-06-12)

> **구조 결정 = Payment 를 독립 애그리거트로.** 그동안 `Order.status`/`paymentTransactionId` 에 묻어 있던 결제 상태를 `domain.payment.Payment`(REQUESTED/APPROVED/FAILED/CANCELED) 로 분리하고, "외부 호출 *전* `REQUESTED` 영속 → 락 밖 charge → 정산" 2.5구간으로 재구성했다. 멱등키는 별도로 두지 않고(주문 키 재사용) ERD `payments` 와 정렬. 도메인 명명(APPROVED/CANCELED)은 유지하고 ERD 문서를 코드에 맞췄다.

### 8.1 도메인 (전이 규칙) — ✅
- [x] `Payment`(`request` 팩토리 · `approve`/`fail`/`cancel` 전이, Tell-Don't-Ask · 평탄한 시각 필드) · `PaymentStatus` · `PaymentErrorType`
- [x] `PaymentTest` 12건 — request/approve/fail(멱등)/cancel(멱등) 전이 + 금지 전이 `INVALID_PAYMENT_TRANSITION`

### 8.2 인프라 — ✅
- [x] `PaymentEntity`(`order_id` 비유니크=1:N · `transaction_id` UNIQUE · `failure_reason` · `requested_at`/`paid_at`/`canceled_at` · soft delete) · `PaymentJpaRepository`(`@Lock` `findByIdForUpdate` · `findFirstByOrderIdOrderByIdDesc`) · `PaymentRepository`/`Impl`
- [x] `PaymentRepositoryImplIntegrationTest` — REQUESTED 라운드트립 · APPROVED/FAILED 영속 · `findByIdForUpdate` 락 조회

### 8.3 흐름 분리 (Initiator/Settler) — ✅
- [x] `PaymentFacade.pay` = 비-트랜잭션 오케스트레이터(요청→락 밖 charge→정산). `PaymentInitiator`(REQUIRES_NEW: 주문 락 + 진행중 결제 dedupe → REQUESTED 커밋) · `PaymentSettler`(REQUIRES_NEW: 결제 락 + REQUESTED 일 때만 정산=멱등, 성공 approve+PAID / 실패 fail+보상+PAYMENT_FAILED)
- [x] 중복 트리거는 `order_id` UNIQUE 아니라 주문 락 + dedupe 로 단 1건만 REQUESTED → charge 정확히 1회. 기존 통합(OrderCoupon·OrderPaymentFailure·PaymentConcurrency·OrderConcurrency) 전부 그린(행위 보존 + Payment 레코드 추가)
- [x] `PaymentInitiatorTest`·`PaymentSettlerTest`·`PaymentFacadeTest`(오케스트레이션)

### 8.4 취소(환불) — ✅
- [x] `OrderStatus.CANCELED` + `Order.cancel()`(PENDING/PAID→CANCELED, FAILED 거절, 멱등) — `OrderTest` cancel 4건
- [x] `PaymentGateway.refund`(멱등 계약) + `AlwaysSuccessPaymentGateway`
- [x] `OrderCompensator` 추출(재고·쿠폰 보상 — Settler·Canceler 공유, 중복 제거) + `OrderCompensatorTest`
- [x] `PaymentCanceler`(REQUIRES_NEW: 결제 락 + CANCELED 아닐 때만, payment.cancel+order.cancel+보상) · `PaymentFacade.cancel`(승인분 락 밖 refund → 취소 정산)
- [x] `PaymentCancelerTest` · `PaymentFacadeTest` cancel · `PaymentCancelIntegrationTest`(PAID 주문 취소 → 결제·주문 CANCELED + 재고·쿠폰 원복)

### 8.5 향후 과제
- [ ] 인터페이스(취소 트리거 — 사용자/어드민 엔드포인트) 미구현 — 현재 애플리케이션 레벨 커맨드(`PaymentFacade.cancel`)만
- [ ] 실제 PG 연동(현재 `AlwaysSuccess`) + 요청-전-영속 기반 reconcile/스윕(고아 PENDING·미정산 결제)
- [ ] 동시 cancel 시 락 밖 refund 이중 호출 가능성 — PG 환불 멱등키로 대응(현재 트리거 1회라 비활성)

---

## 진행 로그

- 2026-05-28 ~ 05-29: 초기 plan, UC-1~5 베이스라인 구현 (당시 PaymentGateway 드리프트).
- 2026-06-09: v0.3 — 쿠폰 사용 흡수·단일 트랜잭션 롤백·3금액(문서만). (당시 Q3=직접 조율 메모, PG 차주 이관.)
- 2026-06-10: **v0.4 — 코드 리뷰 후 재결정.** 다관점 워크플로(7 에이전트) + 적대적 검증으로 (1) 쿠폰 연동 A 확정(requirements §5 개정), (2) userCouponId 전면 개명, (3) 최소 PG 커밋 후 도입 결정. Tidy-First 구현 순서 수립(구조 개명 → A 전환 → 3금액 → PG → interfaces → 동시성).
- 2026-06-11: **결제 실패 보상 + PaymentFacade 추출(§4.3).** 결제 오케스트레이션을 리스너에서 `PaymentFacade.pay(orderId)`(`@Transactional(REQUIRES_NEW)`) 로 이주하고 리스너를 얇은 트리거로 슬림화. REQUIRES_NEW 가 필수인 이유: AFTER_COMMIT 콜백은 이미 커밋된 원 트랜잭션이 바인딩돼 있어 평범한 `@Transactional` 은 조인만 하고 커밋되지 않는다. 실패 분기 보상 구현(차감 재고 `restoreStock` 원복 + 소진 쿠폰 `cancelUse` 복원 + `PAYMENT_FAILED` 전이, 멱등 가드로 이중 청구·이중 보상 방지). `PAYMENT_FAILED` 가 도달 가능 상태가 됨(운영 게이트웨이는 여전히 항상 성공 mock — 실제 PG 연동만 향후 과제). `PaymentFacadeTest`(단위) + `OrderPaymentFailureIntegrationTest`(실패 게이트웨이 `@MockkBean` → DB 재고·쿠폰·주문상태 보상 검증) 추가, 전체 그린.
