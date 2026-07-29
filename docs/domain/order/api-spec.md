# 주문(Order) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.7)](./requirements.md)
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `orders` · `order_lines` · `coupons` · `user_coupons`
> 작성일: 2026-06-10
> Base URL: 회원 채널 `/api/v1`, 어드민 채널 `/api-admin/v1`

---

## 0. 공통 사항

### 0.1 회원 채널 인증 — 필수
회원 동작(UC-1·UC-2·UC-3)은 회원 인증을 **요구한다**. 인증 헤더(`X-Loopers-LoginId`/`X-Loopers-LoginPw`)가 없거나 식별에 실패하면 `401 UNAUTHORIZED` 로 거부된다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 회원 로그인 식별자 |
| `X-Loopers-LoginPw` | O | 회원 비밀번호 |

### 0.2 어드민 채널 인증
어드민 동작(UC-4·UC-5)은 LDAP 인증을 통과해야 한다. 인증 설계는 [admin/logical-model.md](../admin/logical-model.md) 가 정의하며, 아래 헤더로 운영자를 식별한다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 운영자 식별자 |

`/api-admin/**` 는 경로 기준으로 전수 인증되며(어드민 전용 인터셉터), 회원 채널(`/api/**`)과 분리된다. 헤더 누락·값 불일치는 `401 UNAUTHORIZED`, 식별은 됐으나 권한 부족은 `403 FORBIDDEN` 로 분기한다.

### 0.3 멱등 키
주문 생성(UC-1)은 멱등 키를 **요구한다**. 같은 회원의 같은 멱등 키 재요청은 신규 주문을 만들지 않고 기존 주문과 동일한 응답으로 수렴한다. 멱등 키는 요청 헤더로 전달한다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `Idempotency-Key` | O | 클라이언트가 발급하는 중복 흡수용 식별 문자열. 비어 있으면 `400 IDEMPOTENCY_KEY_BLANK`. |

### 0.4 쿠폰 식별자(userCouponId) 표기
주문 요청·응답의 쿠폰 식별자 필드명은 **`userCouponId`** 다 — 회원이 보유한 **발급 쿠폰**(`UserCoupon`) 의 식별자이며, 쿠폰 **템플릿**(`Coupon`, coupon 채널에서 `couponId`) 이 아니다. 소유·사용 여부 검증이 이 식별자 기준으로 성립한다. 미적용 주문은 요청에서 생략(`null`)하고, 응답에서도 `null` 로 노출된다.

> 원 과제(week4) 요청 예시는 와이어 필드를 `couponId` 로 적었으나, 본 명세는 식별자의 실제 의미(발급 쿠폰)를 와이어에 그대로 드러내기 위해 **`userCouponId`** 로 명명한다.

### 0.5 주문 상태(status) 표기
와이어 값은 도메인 `OrderStatus` 의 이름을 그대로 쓴다(대문자). 주문 생성 직후의 상태는 `CREATED` 다. 회원이 결제를 요청하면(결제 도메인 `POST /api/v1/payments`) 주문이 `PAYMENT_PENDING` 으로 전이하고, 결제가 반영된 뒤 `PAID`(성공) 또는 `PAYMENT_FAILED`(실패) 로 마감된다. **주문 생성(UC-1) 응답의 상태는 `CREATED`** 다 — 결제는 별도 명시 흐름이라 생성 응답 시점엔 아직 결제 전이다. **이후 조회(UC-2·3·4·5) 응답의 상태는 결제 진행/결과에 따라 `PAYMENT_PENDING` · `PAID` · `PAYMENT_FAILED`** 다.

| 값 | 의미 | 비고 |
|---|---|---|
| `CREATED` | 생성 | 주문 생성 직후의 상태 — 결제 시작 전. **생성 응답의 상태** |
| `PAYMENT_PENDING` | 결제대기(진행) | 회원의 결제 요청이 전이시킨 상태 — 결제 결과 반영 전 |
| `PAID` | 결제완료 | 결제 성공 반영 후 |
| `PAYMENT_FAILED` | 결제실패 | 결제 실패 시 도달하는 마감 상태 — 이후 조회에서 노출된다(재고·쿠폰은 보상으로 원복) |

### 0.6 금액 필드(3금액 스냅샷)
주문 응답은 쿠폰 적용 전/후를 분리해 세 금액을 모두 노출한다.

| 필드 | 의미 |
|---|---|
| `originalAmount` | 상품 합계 — 라인 `unitPrice × quantity` 의 합 (쿠폰 적용 전 금액) |
| `discountAmount` | 할인 금액 — 적용 쿠폰의 할인액, 미적용 시 `0` |
| `totalAmount` | 결제 금액 — `originalAmount − discountAmount`, 0 이상 (최종 결제 대상 금액) |

### 0.7 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `POST` | `/api/v1/orders`             | 회원   | 필수 | 주문 생성 (UC-1) |
| `GET`  | `/api/v1/orders`             | 회원   | 필수 | 내 주문 목록 조회 (UC-2) |
| `GET`  | `/api/v1/orders/{orderId}`   | 회원   | 필수 | 내 주문 상세 조회 (UC-3) |
| `GET`  | `/api-admin/v1/orders`             | 어드민 | 필수 | 어드민 주문 목록 조회 (UC-4) |
| `GET`  | `/api-admin/v1/orders/{orderId}`   | 어드민 | 필수 | 어드민 주문 상세 조회 (UC-5) |

---

## 1. (회원) 주문 생성

### Request
- `POST /api/v1/orders`
- **인증**: 회원 인증 필수 (`§0.1`) + 멱등 키 헤더 (`§0.3`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ],
  "userCouponId": 42   // 미적용 시 생략 가능 (NULLABLE)
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `items` | Array | O | 1개 이상의 주문 라인. 비어 있으면 `400 EMPTY_LINES` |
| `items[].productId` | Long | O | 상품 식별자 |
| `items[].quantity` | Int | O | 1 이상. 1 미만이면 `400 INVALID_QUANTITY` |
| `userCouponId` | Long | X | 적용할 **발급 쿠폰**(`UserCoupon`) 식별자 (`§0.4`). 생략 시 할인 없음 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId":        1001,
    "userId":         7,
    "status":         "CREATED",
    "orderedAt":      "2026-06-10T21:30:00",
    "originalAmount":    35000,
    "discountAmount": 3500,
    "totalAmount":  31500,
    "userCouponId":   42,
    "items": [
      { "productId": 1, "productName": "스투시 반팔티", "unitPrice": 15000, "quantity": 2, "subtotal": 30000 },
      { "productId": 3, "productName": "키링",         "unitPrice": 5000,  "quantity": 1, "subtotal": 5000 }
    ]
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.orderId` | Long | 생성된 주문 식별자 |
| `data.userId` | Long | 주문 소유 회원 식별자 |
| `data.status` | String | 주문 상태 (`§0.5`) — **생성 응답은 `CREATED`** (이후 결제 요청으로 `PAYMENT_PENDING`, 정산으로 `PAID`/`PAYMENT_FAILED` 전이) |
| `data.orderedAt` | String | 주문 시각 (`Asia/Seoul`) |
| `data.originalAmount` | Long | 상품 합계 (쿠폰 적용 전) (`§0.6`) |
| `data.discountAmount` | Long | 할인 금액 — 미적용 시 `0` (`§0.6`) |
| `data.totalAmount` | Long | 결제 금액 (`§0.6`) |
| `data.userCouponId` | Long? | 적용된 발급 쿠폰 식별자 — 미적용 시 `null` (`§0.4`) |
| `data.items[].productId` | Long | 상품 식별자 |
| `data.items[].productName` | String | **시점** 상품명 스냅샷 |
| `data.items[].unitPrice` | Long | **시점** 단가 스냅샷 |
| `data.items[].quantity` | Int | 주문 수량 |
| `data.items[].subtotal` | Long | `unitPrice × quantity` |

> **처리 순서**: 재고 차감 · 쿠폰 사용 · 주문 저장(`CREATED`) 이 단일 트랜잭션으로 처리되고 종료된다. **결제는 별도 명시 흐름** — 회원이 결제(Payment) 도메인의 `POST /api/v1/payments` 를 호출하면 주문이 `PAYMENT_PENDING` 으로 전이하고, 콜백/폴링 정산이 `PAID`(성공) 또는 `PAYMENT_FAILED`(실패 — 재고·쿠폰 보상 후) 로 마감한다. **생성 응답 자체는 `CREATED`** 이며, 결제 진행·결과는 이후 조회에서 드러난다(결제 흐름은 [payment/api-spec.md](../payment/api-spec.md)).
> **멱등 재요청**: 같은 회원이 같은 `Idempotency-Key` 로 재요청하면 신규 주문을 만들지 않고 기존 주문과 동일한 본문으로 `200 OK` 를 응답한다.

### 실패 응답

실패 응답 본문 형식은 템플릿 §0.3 을 참조한다. 아래 케이스는 모두 주문 저장 트랜잭션 안에서 발생하며, 트랜잭션이 전부 롤백되어 주문·재고·쿠폰 어떤 변경도 남지 않는다. 결제는 주문 생성과 분리된 별도 명시 흐름이므로 **생성 응답 자체에는 결제-실패 에러가 없고, 생성 응답의 상태는 항상 `CREATED`** 다. 결제가 실패하면 그 사실은 생성 응답이 아니라 **이후 조회**에서 주문이 `PAYMENT_FAILED` 로 드러나며(차감된 재고·소진된 쿠폰은 보상으로 원복), 자세한 흐름은 §부록을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `IDEMPOTENCY_KEY_BLANK` | `Idempotency-Key` 헤더가 비어 있음 |
| `400` | `EMPTY_LINES` | `items` 가 비어 있음 |
| `400` | `INVALID_QUANTITY` | 라인의 수량이 1 미만 |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 (`§0.1`) |
| `404` | `PRODUCT_NOT_FOUND` | 라인 중 존재하지 않는 상품 |
| `409` | `INSUFFICIENT_STOCK` | 라인 중 재고 부족 |
| `404` | `USER_COUPON_NOT_FOUND` | 발급 쿠폰이 없거나 타 유저 소유 |
| `409` | `ALREADY_USED_COUPON` | 이미 사용된 쿠폰 |
| `400` | `COUPON_NOT_APPLICABLE` | 만료된 쿠폰 / 상품 합계가 최소 주문 금액 미달 |

---

## 2. (회원) 내 주문 목록 조회

### Request
- `GET /api/v1/orders?startAt={iso}&endAt={iso}&page=0&size=20`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `startAt` | String(ISO-8601) | X | 기간 시작. 생략 시 하한 없음 |
| `endAt` | String(ISO-8601) | X | 기간 끝. 생략 시 상한 없음. `startAt > endAt` 이면 `400 INVALID_DATE_RANGE` |
| `page` | Int | X | 0부터. 기본 `0`. 음수면 `400 BAD_REQUEST` |
| `size` | Int | X | 기본 `20`. 허용 범위 밖이면 `400 BAD_REQUEST` |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "orderId":        1001,
        "status":         "PAID",
        "orderedAt":      "2026-06-10T21:30:00",
        "originalAmount":    35000,
        "discountAmount": 3500,
        "totalAmount":  31500,
        "userCouponId":   42,
        "items": [
          { "productId": 1, "productName": "스투시 반팔티", "unitPrice": 15000, "quantity": 2, "subtotal": 30000 }
        ]
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content[]` | Array | 주문 시각 내림차순 |
| `data.content[].orderId` | Long | 주문 식별자 |
| `data.content[].status` | String | 주문 상태 (`§0.5`) |
| `data.content[].orderedAt` | String | 주문 시각 |
| `data.content[].originalAmount` | Long | 상품 합계 |
| `data.content[].discountAmount` | Long | 할인 금액 |
| `data.content[].totalAmount` | Long | 결제 금액 |
| `data.content[].userCouponId` | Long? | 적용 발급 쿠폰 식별자 — 미적용 시 `null` |
| `data.content[].items[]` | Array | 라인 요약 (상품 생성과 동일 필드) |
| `data.page` / `size` / `totalElements` / `totalPages` | — | 페이지 메타 |

> 결과가 비어 있으면 `content: []` 와 페이지 메타로 응답한다 (실패가 아니다).

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | `page` 음수 / `size` 허용 범위 밖 |
| `400` | `INVALID_DATE_RANGE` | `startAt > endAt` |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 |

---

## 3. (회원) 내 주문 상세 조회

### Request
- `GET /api/v1/orders/{orderId}`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `orderId` | Long | O | 조회할 주문 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId":        1001,
    "userId":         7,
    "status":         "PAID",
    "orderedAt":      "2026-06-10T21:30:00",
    "originalAmount":    35000,
    "discountAmount": 3500,
    "totalAmount":  31500,
    "userCouponId":   42,
    "items": [
      { "productId": 1, "productName": "스투시 반팔티", "unitPrice": 15000, "quantity": 2, "subtotal": 30000 },
      { "productId": 3, "productName": "키링",         "unitPrice": 5000,  "quantity": 1, "subtotal": 5000 }
    ]
  }
}
```

필드는 §1 주문 생성 응답과 동일하다.

### 실패 응답

존재하지 않는 주문과 타인 주문은 응답이 구분된다 (`ORDER_NOT_FOUND` vs `ORDER_FORBIDDEN`).

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 회원 인증 실패 |
| `404` | `ORDER_NOT_FOUND` | 존재하지 않는 주문 식별자 |
| `403` | `ORDER_FORBIDDEN` | 타인 소유 주문 식별자 |

---

## 4. (어드민) 주문 목록 조회

### Request
- `GET /api-admin/v1/orders?page=0&size=20`
- **인증**: LDAP 인증 필수 (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `page` | Int | X | 0부터. 기본 `0`. 음수면 `400 BAD_REQUEST` |
| `size` | Int | X | 기본 `20`. 허용 범위 밖이면 `400 BAD_REQUEST` |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "orderId":             1001,
        "userId":              7,
        "userMaskedName":      "김민*",
        "status":              "PAID",
        "orderedAt":           "2026-06-10T21:30:00",
        "originalAmount":         35000,
        "discountAmount":      3500,
        "totalAmount":       31500,
        "userCouponId":        42,
        "paymentTransactionId": "tx-20260610-0001",
        "paymentResultCode":    "APPROVED",
        "items": [
          { "productId": 1, "productName": "스투시 반팔티", "unitPrice": 15000, "quantity": 2, "subtotal": 30000 }
        ]
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content[]` | Array | 전사 주문, 주문 시각 내림차순 |
| `data.content[].userId` | Long | 회원 식별자 (운영 메타) |
| `data.content[].userMaskedName` | String | 회원 표시명 — 마지막 1글자 `*` 마스킹 (운영 메타) |
| `data.content[].paymentTransactionId` | String? | 결제 트랜잭션 식별자 (운영 메타) — 결제 반영(성공·실패) 후 채워지며, 결제 전이면 `null` |
| `data.content[].paymentResultCode` | String? | 결제 결과 코드 (운영 메타) — 예: 성공 `APPROVED`, 실패 `DECLINED` |
| 나머지 | — | §2 회원 목록과 동일 |

> 운영 메타에 카드 번호 · CVC 등 결제 민감 정보는 포함되지 않는다.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | `page` 음수 / `size` 허용 범위 밖 |
| `401` | `UNAUTHORIZED` | LDAP 식별 실패 (`§0.2`) |
| `403` | `FORBIDDEN` | LDAP 식별은 통과, 주문 조회 권한 부족 |

---

## 5. (어드민) 주문 상세 조회

### Request
- `GET /api-admin/v1/orders/{orderId}`
- **인증**: LDAP 인증 필수 (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `orderId` | Long | O | 조회할 주문 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**: §4 어드민 목록의 `content[]` 단일 항목과 동일한 형태(운영 메타 포함). 운영자는 어느 회원의 주문이든 본인 한정 제약 없이 조회한다 — 회원 시각의 `ORDER_FORBIDDEN` 분기는 발생하지 않는다.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | LDAP 식별 실패 |
| `403` | `FORBIDDEN` | 주문 조회 권한 부족 |
| `404` | `ORDER_NOT_FOUND` | 존재하지 않는 주문 식별자 |

---

## 부록 — 쿠폰/결제 연동 메모

- **쿠폰 사용 결선**: 주문 생성 시 쿠폰 사용(소유·사용·만료·최소금액 검증 + 할인 계산 + 소진)은 `OrderFacade` 가 **쿠폰 도메인 객체·리포지토리를 자신의 트랜잭션 안에서 직접 조율**한다(`CouponFacade.applyCoupon` 경유 아님). 할인 계산식·단일 사용·만료 판정 규칙 자체는 Coupon 도메인이 소유한다. 쿠폰 사용은 독립 엔드포인트로 노출하지 않는다.
- **결제 진입(명시 API)**: 결제는 회원이 직접 호출하는 결제(Payment) 도메인 API(`POST /api/v1/payments`, `application.payment.PaymentFacade.pay(command)`)가 처리한다. 주문 생성과 분리된 별도 흐름이며, 자동 결제 이벤트(`OrderPlacedEvent`)·항상성공 mock(`AlwaysSuccessPaymentGateway`)·구 `application.order.port.PaymentGateway` 는 제거되었다.
- **결제 진행 전이**: `pay` 는 주문을 잠금 조회해 `Order.validatePayable()`(`CREATED` 만 결제 가능) 로 검증하고 `Order.markPaymentPending()` 으로 `CREATED → PAYMENT_PENDING` 전이시킨다. 동시·중복 결제 요청은 주문 잠금 + 단방향 전이로 차단된다(두 번째는 `validatePayable` 가 `ORDER_NOT_PAYABLE` 로 거부). 외부 PG 연동·접수·정산은 [payment/api-spec.md](../payment/api-spec.md) 가 소유한다.
- **결제 실패 보상**: 결제 실패 정산 시 재고 원복(`Product.restoreStock`)·쿠폰 사용 취소(`UserCoupon.cancelUse`) 후 `Order.markPaymentFailed(...)` 로 `PAYMENT_FAILED` 전이한다(멱등). 이 전이·보상 규칙은 주문(및 Product·Coupon) 도메인이 소유하고, **언제 호출하는지(조율)** 는 결제 정산이 트리거한다(payment 도메인).
- **락 전략**: 발급 쿠폰 단일 사용은 Coupon 도메인이 비관적 행 락(`findByIdForUpdate`)을 제공한다. 재고 차감 락(비관/낙관)은 **TBD** — requirements §미해결 결정 사항 참조. 보상의 `restoreStock` 도 차감과 같은 read-modify-write 라 동일한 재고 락 TBD 선상에 있다.
