# 좋아요(Like) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.2)](./requirements.md)
> 작성일: 2026-06-08
> Base URL: `/api/v1`

---

## 0. 공통 사항

### 0.1 헤더 기반 사용자 식별

본 도메인의 모든 엔드포인트는 인증을 요구한다 (requirements §4 공통 사전 단계). 아래 두 헤더를 **모두** 요구한다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 로그인 ID |
| `X-Loopers-LoginPw` | O | 비밀번호 (User 도메인 식별 절차 그대로 사용) |

식별 절차 (requirements E0):
1. 헤더 중 하나라도 누락/공백 → `401 UNAUTHORIZED`
2. `LoginId` 미존재 또는 `LoginPw` 불일치 → `401 UNAUTHORIZED` (응답에서 원인 구분 X)

인증으로 식별된 회원이 좋아요 등록·취소의 주체이며, 내 목록 조회 시 경로의 사용자 식별자와 대조되는 기준이 된다.

### 0.2 엔드포인트 일람

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST`   | `/api/v1/products/{productId}/likes` | 헤더 필수 | 좋아요 등록 (멱등 수렴) |
| `DELETE` | `/api/v1/products/{productId}/likes` | 헤더 필수 | 좋아요 취소 (멱등 수렴) |
| `GET`    | `/api/v1/users/{userId}/likes`       | 헤더 필수 | 내 좋아요 목록 조회 |

> 좋아요는 `(회원, 상품)` 관계이므로 등록·취소 대상은 상품 하위 리소스(`/products/{productId}/likes`) 로, 내 목록은 사용자 하위 리소스(`/users/{userId}/likes`) 로 표현한다. 등록·취소의 주체 회원은 경로가 아니라 인증 헤더로 식별한다.

---

## 1. 좋아요 등록

### Request
- `POST /api/v1/products/{productId}/likes`
- **인증**: 헤더 필수 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 좋아요 대상 상품 식별자 |

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
| `data` | null | 빈 페이로드 — 토글 결과 상태(`liked`, `likeCount`) 는 본 도메인 응답으로 돌려주지 않는다 (requirements §5) |

> 멱등 수렴: 이미 좋아요한 상태에서 다시 요청해도 추가 등록 없이 동일한 `200 OK` / `data: null` 을 응답한다.

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 헤더 누락, loginId 미존재, 비밀번호 불일치 (응답에서 구분 X) |
| `404` | `PRODUCT_NOT_FOUND` | `productId` 상품이 존재하지 않음 — 무동작 멱등으로 흡수하지 않고 실패로 분기 |

---

## 2. 좋아요 취소

### Request
- `DELETE /api/v1/products/{productId}/likes`
- **인증**: 헤더 필수 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 좋아요 취소 대상 상품 식별자 |

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
| `data` | null | 빈 페이로드 — 취소 결과 상태를 응답으로 돌려주지 않는다 |

> 멱등 수렴: 좋아요가 없는 상태에서 취소를 요청해도 무동작으로 동일한 `200 OK` / `data: null` 을 응답한다.

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 헤더 누락, loginId 미존재, 비밀번호 불일치 (응답에서 구분 X) |
| `404` | `PRODUCT_NOT_FOUND` | `productId` 상품이 존재하지 않음 — 무동작 멱등으로 흡수하지 않고 실패로 분기 |

---

## 3. 내 좋아요 목록 조회

### Request
- `GET /api/v1/users/{userId}/likes?page={page}&size={size}`
- **인증**: 헤더 필수 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `userId` | Long | O | 조회 대상 회원 식별자. **인증된 회원의 식별자와 일치해야 한다** — 타인 식별자 요청은 `403` 으로 거부 |

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작. Spring `Pageable` 이 처리하며 음수는 `0` 으로 **보정**된다 |
| `size` | Int | X | `20` | 페이지 크기. 상한(`max-page-size`)을 초과하면 상한값으로 **보정**된다 |

> 페이징은 Spring `Pageable`(`@PageableDefault(size = 20)`) 로 수신하므로, 음수 `page`/과대 `size` 는 거부(400)가 아니라 정규화된다. 정렬은 서버 고정(likedAt desc) 이라 `sort` 파라미터는 무시한다.

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
        "productId": 101,
        "name":      "운동화",
        "price":     59000,
        "likeCount": 42,
        "brandId":   7
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
| `data.content` | Array | 좋아요한 상품 요약 목록. 좋아요한 시각 **내림차순**(최근순) 정렬. 좋아요한 상품이 없으면 빈 배열 `[]` |
| `data.content[].productId` | Long | 상품 식별자 |
| `data.content[].name` | String | 상품 이름 |
| `data.content[].price` | Long | 상품 가격 |
| `data.content[].likeCount` | Long | 상품의 좋아요 수 |
| `data.content[].brandId` | Long | 브랜드 식별자 |
| `data.page` | Int | 현재 페이지 번호 (0부터) |
| `data.size` | Int | 페이지 크기 |
| `data.totalElements` | Long | 전체 좋아요 건수 |
| `data.totalPages` | Int | 전체 페이지 수 |

> 좋아요한 시각은 정렬 기준으로만 사용되며 응답 항목에 포함하지 않는다 (requirements §5).

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 헤더 누락, loginId 미존재, 비밀번호 불일치 (응답에서 구분 X) |
| `403` | `LIKE_FORBIDDEN` | 경로의 `userId` 가 인증된 회원과 다름 — 타인의 좋아요 목록 조회 불가 |

> requirements §UC-3 E2 는 음수 `page`/범위 밖 `size` 를 `400` 으로 규정했으나, 구현은 Spring `Pageable` 로 페이징을 수신해 해당 입력을 **정규화**(음수 page→0, 과대 size→상한)한다. 따라서 페이징 입력으로 인한 `400` 은 발생하지 않는다.

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 모든 엔드포인트 — 헤더 누락/인증 실패 |
| `PRODUCT_NOT_FOUND` | 404 | 좋아요 등록·취소 — 대상 상품 미존재 (`ProductErrorType.PRODUCT_NOT_FOUND`) |
| `LIKE_FORBIDDEN` | 403 | 내 목록 조회 — 경로 `userId` 와 인증 회원 불일치 (`LikeErrorType.LIKE_FORBIDDEN`) |

> requirements §UC-3 E1 은 일반 `FORBIDDEN` 으로 표기했으나, 실제 구현은 도메인 전용 코드 `LIKE_FORBIDDEN` (HTTP 403) 을 사용한다. 본 명세는 구현을 따른다.

## 부록 — 구현 메모 / 미해결 사항

> `LikeV1Controller` 구현 완료. 인증은 `@RequireAuth` + `@LoginUser user: AuthUser`(인증 회원의 `id`·`loginId` 를 담는 주체 VO), 페이징은 `Pageable` 수신 → 프레임워크 독립 `PageQuery`/`PageResult` 로 변환한다.

- **페이지 메타** *(해소)*: `LikeFacade.getMyLikes` 가 `PageResult<LikedProductResult>` 를 반환하며, 응답 `data` 는 `content` + `page`/`size`/`totalElements`/`totalPages` 봉투다. (이전 "평면 배열" TBD 해소)
- **브랜드 요약** *(미해결)*: requirements 는 항목에 "브랜드 요약" 을 명시하나, 현재 `LikedProductItem` 은 `brandId: Long` 만 담는다. 브랜드 이름 등 요약 확장은 `product`/`brand` 도메인 연동 시 결정한다.
- **`size` 상한**: requirements §6 의 "합리적 상한" 은 Spring `spring.data.web.pageable.max-page-size` 설정으로 캡한다(미설정 시 프레임워크 기본값).
- requirements §5 의 TBD(좋아요 수 진실의 원천, 판매 중지/삭제 상품의 목록 노출 정책) 는 본 API 응답 계약에 직접 영향을 줄 수 있어 추적 대상이다.
