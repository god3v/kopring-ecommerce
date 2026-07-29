# 쿠폰(Coupon) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.3)](./requirements.md)
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `coupons` · `user_coupons`
> 작성일: 2026-06-09
> Base URL: 회원 채널 `/api/v1`, 관리자 채널 `/api-admin/v1`

---

## 0. 공통 사항

### 0.1 회원 채널 인증 — 필수
회원 동작(UC-1·UC-2)은 회원 인증을 **요구한다**. 인증 헤더(`X-Loopers-LoginId`/`X-Loopers-LoginPw`)가 없거나 식별에 실패하면 `401 UNAUTHORIZED` 로 거부된다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 회원 로그인 식별자 |
| `X-Loopers-LoginPw` | O | 회원 비밀번호 |

### 0.2 관리자 채널 인증
관리자 동작(UC-3~8)은 관리자 인증을 통과해야 한다. 인증 설계는 [admin/logical-model.md](../admin/logical-model.md) 가 정의하며, 아래 헤더로 관리자를 식별한다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 관리자 식별자 |

`/api-admin/**` 는 경로 기준으로 전수 인증되며(관리자 전용 인터셉터), 회원 채널(`/api/**`)과 분리된다. 헤더 누락 또는 값 불일치는 `401 UNAUTHORIZED` 로 분기한다.

### 0.3 할인 종류(type) 표기
와이어 값은 도메인 `DiscountType` 의 이름을 그대로 쓴다(대문자).

| 값 | 의미 | `value` 의미 |
|---|---|---|
| `FIXED` | 정액 | 할인 금액(원) |
| `RATE` | 정률 | 할인 비율(%) — 1~100 |

### 0.4 발급 쿠폰 상태(status) 표기
와이어 값은 노출 상태의 이름을 그대로 쓴다(대문자). `EXPIRED` 는 저장값이 아니라 **발급 쿠폰 자신의 사용 만료 시각(`expiredAt`) 경과**로 파생되는 노출 상태다(템플릿 무관).

| 값 | 의미 |
|---|---|
| `AVAILABLE` | 사용 가능 |
| `USED` | 사용 완료 |
| `EXPIRED` | 만료 (미사용 + 발급 쿠폰의 사용 만료 시각 경과) |

### 0.5 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `POST`   | `/api/v1/coupons/{couponId}/issue`            | 회원   | 필수 | 쿠폰 발급 요청 (UC-1) |
| `POST`   | `/api/v1/coupons/issue`                       | 회원   | 필수 | 선착순 쿠폰 발급 요청 접수 (UC-10) |
| `GET`    | `/api/v1/coupons/issue/{requestId}`           | 회원   | 필수 | 선착순 발급 요청 결과 조회 (UC-11) |
| `GET`    | `/api/v1/users/me/coupons`                    | 회원   | 필수 | 내 쿠폰 목록 조회 (UC-2) |
| `GET`    | `/api-admin/v1/coupons`                       | 관리자 | 필수 | 쿠폰 템플릿 목록 조회 (UC-3) |
| `GET`    | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 상세 조회 (UC-4) |
| `POST`   | `/api-admin/v1/coupons`                       | 관리자 | 필수 | 쿠폰 템플릿 등록 (UC-5) |
| `PUT`    | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 수정 (UC-6) |
| `DELETE` | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 삭제 (UC-7) |
| `GET`    | `/api-admin/v1/coupons/{couponId}/issues`     | 관리자 | 필수 | 특정 쿠폰 발급 내역 조회 (UC-8) |

> 쿠폰 **사용**(UC-9, 할인 계산 + 단일 사용 소진) 은 회원이 직접 호출하는 엔드포인트로 노출하지 않는다 — **주문 생성 시(`POST` 주문) 적용**된다. 주문은 발급 쿠폰 식별자(`couponId`) 를 입력으로 한 장만 받아 같은 트랜잭션에서 소진하며, 적용 실패 시 주문이 실패한다(부록 참조).

---

## 1. (회원) 쿠폰 발급 요청

### Request
- `POST /api/v1/coupons/{couponId}/issue`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 발급받을 쿠폰 **템플릿** 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userCouponId":   501,
    "couponId":       7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "usableFrom":     "2026-06-09T12:00:00",
    "expiredAt":      "2026-12-31T23:59:59",
    "status":         "AVAILABLE",
    "issuedAt":       "2026-06-09T12:00:00"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.userCouponId` | Long | 발급된 쿠폰(인스턴스) 식별자 |
| `data.couponId` | Long | 원본 템플릿 식별자 |
| `data.name` | String | 쿠폰 이름 |
| `data.type` | String | 할인 종류 (`§0.3`) |
| `data.value` | Long | 할인 값 (`§0.3`) |
| `data.minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.usableFrom` | DateTime | 사용 시작 시각 (발급 시점 템플릿 `useStartAt` 스냅샷) |
| `data.expiredAt` | DateTime | 사용 만료 시각 (발급 시점 템플릿 `useEndAt` 스냅샷) |
| `data.status` | String | 발급 직후 항상 `AVAILABLE` (`§0.4`) |
| `data.issuedAt` | DateTime | 발급 시각 |

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_NOT_APPLICABLE` | 발급 가능 기간이 아님 (발급 시작 전 또는 종료 후) |
| `400` | `COUPON_NOT_APPLICABLE` | 선착순 전용(발급 한도 보유) 템플릿 — 즉시 발급 경로로 발급 불가 (UC-10 으로 요청) |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 (헤더 누락/계정 없음/비번 불일치) |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |
| `409` | `ALREADY_ISSUED_COUPON` | 같은 템플릿을 이미 발급받음 (1인 1매) |

---

## 2. (회원) 내 쿠폰 목록 조회

### Request
- `GET /api/v1/users/me/coupons?page={page}&size={size}`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 페이지 번호. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **발급 최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "userCouponId":   501,
        "couponId":       7,
        "name":           "신규가입 10% 할인",
        "type":           "RATE",
        "value":          10,
        "minOrderAmount": 10000,
        "usableFrom":     "2026-06-09T12:00:00",
        "expiredAt":      "2026-12-31T23:59:59",
        "status":         "AVAILABLE",
        "issuedAt":       "2026-06-09T12:00:00",
        "usedAt":         null
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
| `data.content` | Array | 회원 보유 발급 쿠폰 목록(발급 최신순). 없으면 빈 배열 `[]` |
| `data.content[].userCouponId` | Long | 발급 쿠폰 식별자 |
| `data.content[].couponId` | Long | 템플릿 식별자 |
| `data.content[].name` | String | 쿠폰 이름 |
| `data.content[].type` | String | 할인 종류 (`§0.3`) |
| `data.content[].value` | Long | 할인 값 (`§0.3`) |
| `data.content[].minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.content[].usableFrom` | DateTime | 사용 시작 시각 (발급 시점 스냅샷) |
| `data.content[].expiredAt` | DateTime | 사용 만료 시각 (발급 시점 스냅샷) |
| `data.content[].status` | String | 노출 상태 (`§0.4`) — `EXPIRED` 는 발급 쿠폰의 `expiredAt` 으로 파생 |
| `data.content[].issuedAt` | DateTime | 발급 시각 |
| `data.content[].usedAt` | DateTime? | 사용 시각. 미사용이면 `null` |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 회원 인증 실패 |

---

## 3. (관리자) 쿠폰 템플릿 목록 조회

### Request
- `GET /api-admin/v1/coupons?page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "id":             7,
        "name":           "신규가입 10% 할인",
        "type":           "RATE",
        "value":          10,
        "minOrderAmount": 10000,
        "issueStartAt":   "2026-06-01T00:00:00",
        "issueEndAt":     "2026-06-30T23:59:59",
        "useStartAt":     "2026-06-01T00:00:00",
        "useEndAt":       "2026-12-31T23:59:59"
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
| `data.content` | Array | 삭제 마크되지 않은 템플릿 목록(최신순). 없으면 빈 배열 `[]` |
| `data.content[].id` | Long | 템플릿 식별자 |
| `data.content[].name` | String | 쿠폰 이름 |
| `data.content[].type` | String | 할인 종류 (`§0.3`) |
| `data.content[].value` | Long | 할인 값 (`§0.3`) |
| `data.content[].minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.content[].issueStartAt` / `issueEndAt` | DateTime | 발급 가능 구간 |
| `data.content[].useStartAt` / `useEndAt` | DateTime | 사용 가능 구간 |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |

---

## 4. (관리자) 쿠폰 템플릿 상세 조회

### Request
- `GET /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 조회 대상 템플릿 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "issueStartAt":   "2026-06-01T00:00:00",
    "issueEndAt":     "2026-06-30T23:59:59",
    "useStartAt":     "2026-06-01T00:00:00",
    "useEndAt":       "2026-12-31T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 템플릿 식별자 |
| `data.name` | String | 쿠폰 이름 |
| `data.type` | String | 할인 종류 (`§0.3`) |
| `data.value` | Long | 할인 값 (`§0.3`) |
| `data.minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.issueStartAt` / `issueEndAt` | DateTime | 발급 가능 구간 |
| `data.useStartAt` / `useEndAt` | DateTime | 사용 가능 구간 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 5. (관리자) 쿠폰 템플릿 등록

### Request
- `POST /api-admin/v1/coupons`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "name":           "신규가입 10% 할인",
  "type":           "RATE",
  "value":          10,
  "minOrderAmount": 10000,
  "issueStartAt":   "2026-06-01T00:00:00",
  "issueEndAt":     "2026-06-30T23:59:59",
  "useStartAt":     "2026-06-01T00:00:00",
  "useEndAt":       "2026-12-31T23:59:59"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 쿠폰 이름. 공백 불가 |
| `type` | String | O | 할인 종류 — `FIXED` / `RATE` (`§0.3`). 그 외 값은 거부 |
| `value` | Long | O | 할인 값. 양수. `RATE` 면 1~100 |
| `minOrderAmount` | Long | X | 최소 주문 금액. 음수 불가. 미지정이면 하한 제약 없음 |
| `issueStartAt` / `issueEndAt` | DateTime | O | 발급 가능 구간. 종료는 시작보다 뒤, 종료는 등록 시점 기준 미래 |
| `useStartAt` / `useEndAt` | DateTime | O | 사용 가능 구간. 종료는 시작보다 뒤 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "issueStartAt":   "2026-06-01T00:00:00",
    "issueEndAt":     "2026-06-30T23:59:59",
    "useStartAt":     "2026-06-01T00:00:00",
    "useEndAt":       "2026-12-31T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 등록된 템플릿 (상세와 동일 형태) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_BAD_REQUEST` | 입력이 필수·형식 규칙을 어김 (이름/할인 종류/할인 값/발급·사용 구간) |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |

---

## 6. (관리자) 쿠폰 템플릿 수정

### Request
- `PUT /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 수정 대상 템플릿 식별자 |

**Request Body**
```jsonc
{
  "name":           "신규가입 15% 할인",
  "type":           "RATE",
  "value":          15,
  "minOrderAmount": 20000,
  "issueStartAt":   "2026-07-01T00:00:00",
  "issueEndAt":     "2026-07-31T23:59:59",
  "useStartAt":     "2026-07-01T00:00:00",
  "useEndAt":       "2027-06-30T23:59:59"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 변경할 이름. 공백 불가 |
| `type` | String | O | 변경할 할인 종류 (`§0.3`) |
| `value` | Long | O | 변경할 할인 값. 양수. `RATE` 면 1~100 |
| `minOrderAmount` | Long | X | 변경할 최소 주문 금액. 음수 불가 |
| `issueStartAt` / `issueEndAt` | DateTime | O | 변경할 발급 가능 구간 |
| `useStartAt` / `useEndAt` | DateTime | O | 변경할 사용 가능 구간 |

> 템플릿의 **사용 가능 구간**은 발급 시점에 발급 쿠폰으로 스냅샷되므로, 본 수정은 이미 발급된 쿠폰의 사용 가능 기간에 소급되지 않는다. 단 할인 종류·값·최소 주문 금액은 여전히 ID 참조라 사용(주문) 시점의 템플릿 값이 적용된다(부록 참조).

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 15% 할인",
    "type":           "RATE",
    "value":          15,
    "minOrderAmount": 20000,
    "issueStartAt":   "2026-07-01T00:00:00",
    "issueEndAt":     "2026-07-31T23:59:59",
    "useStartAt":     "2026-07-01T00:00:00",
    "useEndAt":       "2027-06-30T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 갱신된 템플릿 (상세와 동일 형태) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_BAD_REQUEST` | 변경 입력이 필수·형식 규칙을 어김 |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 7. (관리자) 쿠폰 템플릿 삭제

### Request
- `DELETE /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 삭제 대상 템플릿 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | null | 빈 페이로드. 템플릿을 **삭제 마크**(soft delete) 처리한다. 이미 발급된 쿠폰은 제거하지 않으며 그대로 조회·사용된다 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 이미 삭제 마크됨 |

---

## 8. (관리자) 특정 쿠폰 발급 내역 조회

### Request
- `GET /api-admin/v1/coupons/{couponId}/issues?page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 발급 내역을 조회할 템플릿 식별자 |

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작 |
| `size` | Int | X | `20` | 페이지 크기 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **발급 최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "userCouponId": 501,
        "userId":       42,
        "status":       "USED",
        "issuedAt":     "2026-06-09T12:00:00",
        "usedAt":       "2026-06-10T09:30:00"
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
| `data.content` | Array | 해당 템플릿으로 발급된 발급 쿠폰 목록(발급 최신순). 없으면 빈 배열 `[]` |
| `data.content[].userCouponId` | Long | 발급 쿠폰 식별자 |
| `data.content[].userId` | Long | 발급 회원 식별자 |
| `data.content[].status` | String | 노출 상태 (`§0.4`) |
| `data.content[].issuedAt` | DateTime | 발급 시각 |
| `data.content[].usedAt` | DateTime? | 사용 시각. 미사용이면 `null` |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 9. (회원) 선착순 쿠폰 발급 요청 접수

> 발급 한도가 있는 선착순 템플릿의 발급을 **즉시 접수**하고 요청 식별자를 돌려준다. 접수는 발급 확정이 아니다 — 한도 소진·중복(1인 1매) 은 여기서 실패로 응답하지 않고 뒤이은 처리 결과(§10) 로 확정된다. 형식적으로 유효한 요청이면 항상 `202 Accepted` 로 접수된다.

### Request
- `POST /api/v1/coupons/issue`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "couponId": 7
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 발급받을 선착순 쿠폰 **템플릿** 식별자 (발급 한도를 가진 템플릿) |

### Response
- `202 Accepted`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "requestId":   "b3f1c2a4-5e6d-47a8-9b0c-1d2e3f4a5b6c",
    "couponId":    7,
    "status":      "REQUESTED",
    "requestedAt": "2026-07-02T12:00:00"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.requestId` | String | 발급 요청 식별자. 결과 조회(§10) 의 키 |
| `data.couponId` | Long | 요청한 템플릿 식별자 |
| `data.status` | String | 접수 직후 항상 `REQUESTED` |
| `data.requestedAt` | DateTime | 접수 시각 |

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.
> 한도 소진(`SOLD_OUT`)·중복 발급(`ALREADY_ISSUED`) 은 접수 실패가 아니라 결과(§10) 로 확정되므로 본 표에 없다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_NOT_APPLICABLE` | 발급 한도가 없는 일반 템플릿(선착순 대상 아님) — 즉시 발급(UC-1) 으로 요청 |
| `400` | `COUPON_NOT_APPLICABLE` | 발급 가능 기간이 아님 (발급 시작 전 또는 종료 후) |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 (헤더 누락/계정 없음/비번 불일치) |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 10. (회원) 선착순 발급 요청 결과 조회

> 접수한 발급 요청의 처리 결과를 요청 식별자로 조회한다(폴링). 아직 처리 전이면 `REQUESTED` 를 그대로 돌려주고, 처리되면 `ISSUED`(발급 쿠폰 정보 포함) 또는 `REJECTED`(사유 포함) 로 한 번 확정된다.

### Request
- `GET /api/v1/coupons/issue/{requestId}`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `requestId` | String | O | §9 접수 시 받은 발급 요청 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body** (발급됨)
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "requestId":    "b3f1c2a4-5e6d-47a8-9b0c-1d2e3f4a5b6c",
    "couponId":     7,
    "status":       "ISSUED",
    "rejectReason": null,
    "userCouponId": 501,
    "processedAt":  "2026-07-02T12:00:01"
  }
}
```

**Response Body** (거절됨 — 품절/이미 발급)
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "requestId":    "b3f1c2a4-5e6d-47a8-9b0c-1d2e3f4a5b6c",
    "couponId":     7,
    "status":       "REJECTED",
    "rejectReason": "SOLD_OUT",
    "userCouponId": null,
    "processedAt":  "2026-07-02T12:00:01"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.requestId` | String | 발급 요청 식별자 |
| `data.couponId` | Long | 요청한 템플릿 식별자 |
| `data.status` | String | `REQUESTED`(처리 전) / `ISSUED`(발급됨) / `REJECTED`(거절됨) |
| `data.rejectReason` | String? | `REJECTED` 일 때만 — `SOLD_OUT`(한도 소진) / `ALREADY_ISSUED`(1인 1매 중복). 그 외 `null` |
| `data.userCouponId` | Long? | `ISSUED` 일 때만 발급된 쿠폰(인스턴스) 식별자. 그 외 `null` |
| `data.processedAt` | DateTime? | 결과 확정 시각. `REQUESTED` 면 `null` |

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 회원 인증 실패 |
| `404` | `ISSUE_REQUEST_NOT_FOUND` | 요청 식별자가 존재하지 않거나 본인 소유가 아님 (존재 여부 비노출) |

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `COUPON_BAD_REQUEST` | 400 | 템플릿 등록·수정 입력 위반 (이름/할인 종류/할인 값/발급·사용 구간) |
| `COUPON_NOT_APPLICABLE` | 400 | 발급 — 발급 가능 기간 밖 / 사용 — 사용 가능 기간 밖, 최소 주문 금액 미달 |
| `UNAUTHORIZED` | 401 | 회원/관리자 인증 실패 |
| `COUPON_NOT_FOUND` | 404 | 템플릿 조회·발급·수정·삭제 — 미존재 또는 삭제 마크 |
| `USER_COUPON_NOT_FOUND` | 404 | 쿠폰 사용(UC-9) — 발급 쿠폰 미존재 또는 소유자 불일치 |
| `ISSUE_REQUEST_NOT_FOUND` | 404 | 선착순 발급 요청 결과 조회(UC-11) — 요청 미존재 또는 소유자 불일치 |
| `ALREADY_ISSUED_COUPON` | 409 | 발급 — 같은 템플릿 1인 1매 위반 |
| `ALREADY_USED_COUPON` | 409 | 쿠폰 사용(UC-9) — 이미 사용된 쿠폰 재사용 |

> 선착순 발급 요청의 **거절 사유** `SOLD_OUT`(한도 소진) · `ALREADY_ISSUED`(1인 1매 중복) 은 HTTP 에러가 아니라 요청 결과(§10 `data.rejectReason`) 다 — 접수(§9) 는 `202` 로 성공하고, 거절은 결과 조회에서 확인된다.

---

## 부록 — 구현 메모 / 미해결 사항

- **쿠폰 사용(UC-9) — 주문 적용으로 구현**: 본 명세는 발급·조회·관리 엔드포인트만 노출한다. 사용(할인 계산 + 단일 사용 소진) 은 별도 엔드포인트가 아니라 **주문 생성(`OrderFacade.placeOrder`)** 흐름이 같은 트랜잭션에서 수행한다 — Facade 가 쿠폰 port(`UserCouponRepository`/`CouponRepository`) 를 직접 주입받아 발급 쿠폰을 비관 락으로 조회하고, **할인 계산·최소 주문 금액은 템플릿(`Coupon.calculateDiscount`)** 이, **단일 사용·사용 가능 기간은 발급 쿠폰(`UserCoupon.use`)** 이 각자 캡슐화한다. 쿠폰은 **주문 1건당 한 장**(`PlaceOrderCommand.userCouponId` 단일 슬롯)만 적용되고, 성공 시 발급 쿠폰은 즉시 `USED` 로 소진된다. 적용 실패(`USER_COUPON_NOT_FOUND` 404 — 미존재/타인 소유, `COUPON_NOT_APPLICABLE` 400 — 최소금액 미달/사용 가능 기간 밖, `ALREADY_USED_COUPON` 409 — 이미 사용) 시 예외가 전파되어 주문 전체가 롤백(주문 미생성·재고 차감 원복·쿠폰 미소진)된다. 주문 도메인은 현재 별도 결제 단계가 없어 주문 생성 성공이 곧 쿠폰 확정 소진이다.
- **상태 직렬화**: `type`(`FIXED`/`RATE`) 과 `status`(`AVAILABLE`/`USED`/`EXPIRED`) 의 와이어 값은 enum 이름(대문자) 을 그대로 쓴다 — `product` 의 `salesStatus`(snake_case key) 와 다른 컨벤션이며, 본 도메인 원 스펙의 요청 예시(`"type": "RATE"`)를 따른다.
- **`EXPIRED` 파생**: 저장 상태는 `AVAILABLE`/`USED` 둘뿐이고, `EXPIRED` 는 조회 시 **발급 쿠폰 자신의 `user_coupons.expired_at`(발급 시점 스냅샷) 경과**로 파생한다(만료 배치 불필요, 템플릿 무관).
- **시간 모델·스냅샷**: 템플릿은 발급 가능 구간(`coupons.issue_start_at`/`issue_end_at`) 과 사용 가능 구간(`coupons.use_start_at`/`use_end_at`) 을 가진다. 발급 시 사용 가능 구간을 발급 쿠폰(`user_coupons.usable_from`/`expired_at`) 으로 **스냅샷**하므로, 템플릿 수정·삭제가 미사용 발급분의 사용 가능 기간에 소급되지 않는다. 다만 할인 종류·값·최소 주문 금액은 아직 ID 참조라 사용 시점 템플릿 값이 적용된다 — 이 조건들의 동결이 필요하면 `user_coupons` 에 해당 스냅샷 컬럼을 추가로 도입한다(현재 범위 밖).
- **HTTP 상태**: 등록 성공을 `200 OK` + 생성 리소스로 표기(프로젝트 컨벤션). `201 Created` 채택은 구현 시 결정 가능.
- **`PUT` vs `PATCH`**: 수정은 변경 가능한 필드 전체 치환이라 `PUT` 으로 표기했다.
