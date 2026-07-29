# 결제(Payment) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.1)](./requirements.md)
> 데이터 모델: [logical-model.md](./logical-model.md) (작성 예정)
> 작성일: 2026-06-23
> Base URL: 회원 채널 `/api/v1`, 콜백 채널 `/api/v1`, 어드민 채널 `/api-admin/v1`

---

## 0. 공통 사항

### 0.1 회원 채널 인증 — 필수
결제 요청(§1)은 회원 인증을 **요구한다**. 인증 헤더가 없거나 식별에 실패하면 `401 UNAUTHORIZED` 로 거부된다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 회원 로그인 식별자 |
| `X-Loopers-LoginPw` | O | 회원 비밀번호 |

### 0.2 콜백 채널 — 외부 PG 전용
결제 결과 콜백(§2)은 외부 PG(`pg-simulator`)가 호출하는 엔드포인트다. 회원 인증을 요구하지 않는다.

- 외부 PG 는 결제 요청 시 전달한 통지 주소(`callbackUrl`)로 결과를 통지한다.
- `pg-simulator` 는 `callbackUrl` 이 `http://localhost:8080` 으로 시작하는지 검증하므로, 콜백 경로는 본 앱(`:8080`)의 `/api/v1/payments/callback` 으로 노출한다.

### 0.3 어드민 채널 인증
결제 상태 수동 복구(§3)는 운영자 동작으로, LDAP 인증을 통과해야 한다. 인증 헤더·분기 규칙은 [order/api-spec.md §0.2](../order/api-spec.md) 와 동일하다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 운영자 식별자 |

### 0.4 카드 정보 표기
결제 요청은 카드 종류와 카드 번호를 받는다.

| 필드 | 값/형식 | 비고 |
|---|---|---|
| `cardType` | `SAMSUNG` \| `KB` \| `HYUNDAI` | 외부 PG 가 지원하는 카드사. 그 외 값은 `400 BAD_REQUEST` |
| `cardNo` | `xxxx-xxxx-xxxx-xxxx` (숫자 4자리 × 4, 하이픈 구분) | 형식 위반은 `400 BAD_REQUEST` |

> **민감정보**: 카드 번호(`cardNo`)는 **어떤 응답에도 노출되지 않으며, 로그·메트릭에도 남기지 않는다**. 본 명세의 응답 예시 어디에도 `cardNo` 가 등장하지 않는다. 외부 PG 가 콜백 본문에 `cardNo` 를 실어 보내더라도(§2) 우리 시스템은 이를 사용·저장·로깅하지 않는다.

### 0.5 결제 상태(status) 표기
와이어 값은 도메인 `PaymentStatus` 의 이름을 그대로 쓴다(대문자). 외부 PG 가 비동기라 **결제 요청(§1) 응답의 상태는 항상 `REQUESTED`(처리 중)** 이고, 최종 결과(`APPROVED`/`FAILED`)는 콜백(§2) 또는 폴링 복구(§3)로 비동기 확정된다.

| 값 | 의미 | 비고 |
|---|---|---|
| `REQUESTED` | 요청·처리 중 | 외부 PG 에 결제를 요청하고 결과를 기다리는 상태. **결제 요청 응답의 상태** |
| `APPROVED` | 승인 | 결과 성공 반영 후. 해당 주문은 `PAID` 로 전이 |
| `FAILED` | 실패 | 결과 실패(잘못된 카드 · 한도 초과) 반영 후. 해당 주문은 `PAYMENT_FAILED` 로 전이(재고·쿠폰 보상) |
| `CANCELED` | 취소 | 승인 결제의 환불 또는 처리 중 결제의 청구 전 취소 |

### 0.6 거래 식별자(transactionKey)
외부 PG 가 요청 접수 시 발급하는 식별자다. 콜백·폴링에서 결제건을 매칭하는 기준이다.

- 정상 접수 시 결제 요청 응답에 포함된다.
- 외부 호출이 타임아웃·통신 실패·회로 차단으로 접수 확인을 받지 못하면 `null` 로 응답한다(결제는 `REQUESTED` 로 남아 폴링이 주문 식별자로 복구한다 — §3, 부록).

### 0.7 비동기·회복 모델 메모
- 결제 요청(§1)은 결과를 기다리지 않고 즉시 "접수(처리 중)" 를 응답한다 — 외부 지연·장애가 응답성으로 전파되지 않는다.
- 결과 반영의 **주 경로는 콜백(§2)**, **안전망은 폴링 복구(§3)** 다.
- 주기적 폴링은 내부 스케줄러가 수행하며 공개 API 가 아니다. §3 은 그 폴링과 **동일한 정산 경로**를 운영자가 단건으로 트리거하는 **수동 복구** 엔드포인트다.

### 0.8 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `POST` | `/api/v1/payments`                  | 회원   | 필수   | 결제 요청 (UC-1) |
| `POST` | `/api/v1/payments/callback`         | 콜백   | 불필요 | 결제 결과 콜백 수신·정산 (UC-2) |
| `POST` | `/api-admin/v1/payments/{paymentId}/sync` | 어드민 | 필수   | 결제 상태 수동 복구 (UC-3) |

---

## 1. (회원) 결제 요청

### Request
- `POST /api/v1/payments`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "orderId":  1001,
  "cardType": "SAMSUNG",
  "cardNo":   "1234-5678-9814-1451"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `orderId` | Long | O | 결제할 주문 식별자. 인증 회원 소유이며 `CREATED`(결제 가능) 상태여야 한다. 결제 요청이 접수되면 주문은 `PAYMENT_PENDING` 으로 전이한다 |
| `cardType` | String | O | `SAMSUNG` \| `KB` \| `HYUNDAI` (`§0.4`) |
| `cardNo` | String | O | `xxxx-xxxx-xxxx-xxxx` 형식 (`§0.4`) |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "paymentId":      5001,
    "orderId":        1001,
    "status":         "REQUESTED",
    "transactionKey": "20260623:TR:9577c5",
    "amount":         31500,
    "requestedAt":    "2026-06-23T21:30:00"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.paymentId` | Long | 생성된 결제 식별자 |
| `data.orderId` | Long | 결제 대상 주문 식별자 |
| `data.status` | String | 결제 상태 (`§0.5`) — **요청 응답은 항상 `REQUESTED`(처리 중)** |
| `data.transactionKey` | String? | 외부 거래 식별자 (`§0.6`) — 접수 확인 전(타임아웃·장애·회로 차단)이면 `null` |
| `data.amount` | Long | 결제 금액 — 주문의 `totalAmount` 와 일치 |
| `data.requestedAt` | String | 결제 요청 시각 (`Asia/Seoul`) |

> **즉시 응답**: 결과는 비동기로 확정된다. 회원은 이후 주문 조회([order/api-spec.md](../order/api-spec.md))에서 `PAID`(성공) 또는 `PAYMENT_FAILED`(실패) 를 확인한다.
> **Fallback(일시 장애)**: 외부 호출이 타임아웃·통신 실패·회로 차단(CB Open)이어도 본 API 는 `200 OK` 로 `REQUESTED`(처리 중) 를 응답한다 — 스레드를 점유하지 않으며, `transactionKey` 는 `null` 일 수 있다. 결제는 폴링(§3)으로 복구된다.
> **멱등(진행 중 결제 재요청)**: 같은 주문에 이미 진행 중(`REQUESTED`/`APPROVED`) 결제가 있으면 신규 결제를 만들지 않고 기존 접수 상태를 그대로 응답한다 — 이중 청구를 막는다.

### 실패 응답

실패 응답 본문 형식은 템플릿 §0.3 을 참조한다. 아래 케이스는 모두 외부 PG 호출 *전* 검증 단계에서 발생하며, 결제는 생성되지 않는다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `INVALID_CARD_NUMBER` | `cardNo` 형식 위반 (`xxxx-xxxx-xxxx-xxxx` 아님) |
| `400` | `UNSUPPORTED_CARD_TYPE` | `cardType` 이 `SAMSUNG`·`KB`·`HYUNDAI` 가 아님 |
| `400` | `Bad Request` | 본문 형식 오류(JSON 파싱 실패·필수 필드 누락 등) |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 (`§0.1`) |
| `404` | `ORDER_NOT_FOUND` | 존재하지 않는 주문 식별자 |
| `403` | `ORDER_FORBIDDEN` | 타인 소유 주문 식별자 (존재 여부와 응답이 구분된다) |
| `409` | `ORDER_NOT_PAYABLE` | 주문이 `CREATED`(결제 가능) 가 아님 (이미 결제 진행·완료·실패·취소) |

---

## 2. (콜백) 결제 결과 수신·정산

### Request
- `POST /api/v1/payments/callback`
- **인증**: 불필요 — 외부 PG 전용 (`§0.2`)
- **Content-Type**: `application/json`

**Request Body** — 외부 PG(`pg-simulator`)가 보내는 처리 결과
```jsonc
{
  "transactionKey": "20260623:TR:9577c5",
  "orderId":        "1001",
  "cardType":       "SAMSUNG",
  "cardNo":         "1234-5678-9814-1451",   // 수신만 — 사용·저장·로깅하지 않는다 (§0.4)
  "amount":         31500,
  "status":         "SUCCESS",
  "reason":         null
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `transactionKey` | String | O | 정산 대상 결제를 매칭하는 거래 식별자 |
| `orderId` | String | O | 주문 식별자 |
| `cardType` | String | O | 카드사 |
| `cardNo` | String | O | 카드 번호 — **수신만 하고 사용·저장·로깅하지 않는다** (`§0.4`) |
| `amount` | Long | O | 결제 금액 |
| `status` | String | O | `SUCCESS` \| `FAILED` (외부 PG 처리 결과) |
| `reason` | String? | X | 실패 사유 (예: 한도 초과 / 잘못된 카드). 성공 시 `null` |

### Response
- `200 OK` — 정산 성공/멱등 무시/미매칭 모두 수신을 정상 확인한다.

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | null | 콜백 수신 확인 — 본문 없음 |

> **정산(멱등)**: 대상 결제가 처리 중(`REQUESTED`)일 때만 1회 반영한다. `status=SUCCESS` 면 결제 `APPROVED` + 주문 `PAID`, `status=FAILED` 면 결제 `FAILED` + 보상(재고·쿠폰 원복) + 주문 `PAYMENT_FAILED`. 콜백 2회 또는 콜백과 폴링이 겹쳐 도착해도 결과는 한 번만 확정된다.
> **미매칭 흡수**: `transactionKey` 에 해당하는 결제가 우리 시스템에 없으면, 정산 없이 수신만 확인하고 `200 OK` 를 응답한다 — 외부 통지를 거부해 재시도 폭주를 유발하지 않는다.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | 콜백 본문 형식 위반 (`transactionKey`/`status` 누락 등) |

---

## 3. (어드민) 결제 상태 수동 복구

### Request
- `POST /api-admin/v1/payments/{paymentId}/sync`
- **인증**: LDAP 인증 필수 (`§0.3`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `paymentId` | Long | O | 복구(상태 동기화)할 결제 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "paymentId":      5001,
    "orderId":        1001,
    "status":         "APPROVED",
    "transactionKey": "20260623:TR:9577c5",
    "settled":        true
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.paymentId` | Long | 복구 시도한 결제 식별자 |
| `data.orderId` | Long | 결제 대상 주문 식별자 |
| `data.status` | String | 복구 후 결제 상태 (`§0.5`) — 외부가 확정되면 `APPROVED`/`FAILED`, 아직 처리 중이거나 외부 조회 실패면 `REQUESTED` 유지 |
| `data.transactionKey` | String? | 외부 거래 식별자 — 미확보 상태였다면 주문 식별자로 외부 조회 후 채워질 수 있다 |
| `data.settled` | Boolean | 이번 호출로 최종 상태(`APPROVED`/`FAILED`)가 확정됐으면 `true`, 변화 없으면 `false` |

> **복구 경로(폴링과 동일)**: 외부 PG 상태를 거래 식별자로 조회해 확정(성공/실패)이면 §2 와 동일하게 정산한다(멱등). 요청 타임아웃으로 `transactionKey` 가 없던 결제는 주문 식별자로 외부 결제건을 조회해 매칭한다 — "타임아웃됐지만 외부는 성공" 케이스가 `APPROVED`(주문 `PAID`)로 수렴한다.
> **미확정 시**: 외부가 아직 처리 중이거나 외부 조회 자체가 실패하면 상태를 바꾸지 않고 `settled: false` 로 응답한다 (실패가 아니다 — 다음 주기/재호출에 재시도).

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | LDAP 식별 실패 (`§0.3`) |
| `403` | `FORBIDDEN` | LDAP 식별은 통과, 결제 조회 권한 부족 |
| `404` | `PAYMENT_NOT_FOUND` | 존재하지 않는 결제 식별자 |

---

## 부록 — PG 연동·회복 전략 메모

- **외부 PG 계약**: `POST {pg}/api/v1/payments`(요청 → `transactionKey`+`PENDING` 접수) · `GET {pg}/api/v1/payments/{transactionKey}`(상태 조회) · `GET {pg}/api/v1/payments?orderId=`(주문별 조회). 외부 요청 헤더는 `X-USER-ID`. 외부는 비동기다 — 요청 성공 60% / 요청 지연 100~500ms / 처리 지연 1~5s / 처리 결과 성공 70%·한도 초과 20%·잘못된 카드 10%.
- **Timeout**: 외부 호출에 연결(connect)·응답(read) 타임아웃을 분리 설정한다. 구체 값은 회복 전략 결정 문서/`logical-model.md` 에서 근거와 함께 정한다(requirements §미해결).
- **CircuitBreaker**: 외부 반복 실패 시 호출을 차단(Open)하고, 차단 동안의 요청은 외부를 호출하지 않고 즉시 Fallback(§1 `REQUESTED` 응답)으로 처리한다.
- **Fallback 분기(영구 vs 일시)**: 잘못된 카드·한도 초과(영구 실패)는 콜백/폴링 정산에서 즉시 `FAILED`. 통신 실패·타임아웃·회로 차단(일시 장애)은 `REQUESTED` 유지 후 폴링 복구. 이 분기는 **요청(§1) 시점이 아니라 정산(§2·§3) 시점**에 결정된다 — §1 응답은 두 경우 모두 `REQUESTED` 다.
- **멱등**: 1주문 1결제. 같은 주문 동시/중복 결제 요청은 진행 중 결제 dedupe 로 1건만 생성된다. 콜백·폴링이 겹쳐도 정산은 처리 중일 때 1회만 반영된다.
- **상태 불일치 복구 시나리오**: (1) 요청 타임아웃으로 `transactionKey` 미확보 → §3 이 주문 식별자로 조회해 수렴. (2) 콜백 유실(외부는 `SUCCESS`인데 통지 안 옴) → 내부 스케줄러 폴링 또는 §3 수동 복구가 거래 식별자로 조회해 수렴.
- **내부 스케줄러 폴링**: 처리 중(`REQUESTED`) 결제를 주기적으로 외부 상태 확인해 정산하는 경로는 내부 스케줄러가 수행하며 공개 API 가 아니다(§3 과 동일 정산 경로).
- **결제 취소·환불 (보류)**: pg-simulator 에 환불/취소 엔드포인트가 없어 외부 환불이 불가능하다 — 외부 `refund` + 취소 오케스트레이션을 제거했다(requirements UC-4 보류). 환불 가능 PG 연동 시 도입한다.
