# 브랜드(Brand) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.1)](./requirements.md) — 회원 단일 조회(UC-1), 관리자 브랜드(UC-2~6).
> 작성일: 2026-06-09
> Base URL: 회원 채널 `/api/v1`, 관리자 채널 `/api-admin/v1`

---

## 0. 공통 사항

### 0.1 회원 채널 인증
단일 브랜드 조회(UC-1)는 **인증을 요구하지 않는다**.

### 0.2 관리자 채널 인증
관리자 동작(UC-2~6)은 관리자 인증을 통과해야 한다. 인증 설계는 [admin/logical-model.md](../admin/logical-model.md) 가 정의하며, 아래 헤더로 관리자를 식별한다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 관리자 식별자 |

`/api-admin/**` 는 경로 기준으로 전수 인증되며(관리자 전용 인터셉터), 회원 채널(`/api/**`)과 분리된다. 헤더 누락 또는 값 불일치는 `401 UNAUTHORIZED` 로 분기한다. (관리자는 별도 도메인 모델/계정 저장소를 두지 않는다 — 헤더 식별만.)

### 0.3 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `GET`    | `/api/v1/brands/{brandId}`        | 회원   | 불필요 | 단일 브랜드 조회 (UC-1) |
| `GET`    | `/api-admin/v1/brands`            | 관리자 | 필수 | 브랜드 목록 조회 (UC-2) |
| `GET`    | `/api-admin/v1/brands/{brandId}`  | 관리자 | 필수 | 브랜드 상세 조회 (UC-3) |
| `POST`   | `/api-admin/v1/brands`            | 관리자 | 필수 | 브랜드 등록 (UC-4) |
| `PUT`    | `/api-admin/v1/brands/{brandId}`  | 관리자 | 필수 | 브랜드 정보 수정 (UC-5) |
| `DELETE` | `/api-admin/v1/brands/{brandId}`  | 관리자 | 필수 | 브랜드 삭제 — 카스케이드 (UC-6) |

---

## 1. (회원) 단일 브랜드 조회

### Request
- `GET /api/v1/brands/{brandId}`
- **인증**: 불필요
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `brandId` | Long | O | 조회 대상 브랜드 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":   7,
    "name": "나이키"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 브랜드 식별자 |
| `data.name` | String | 브랜드 이름 |

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `404` | `BRAND_NOT_FOUND` | 브랜드가 존재하지 않거나 삭제 마크됨 |

---

## 2. (관리자) 브랜드 목록 조회

### Request
- `GET /api-admin/v1/brands?page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **최신순 고정** (정렬 옵션 입력 없음)

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      { "id": 7, "name": "나이키" }
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
| `data.content` | Array | 삭제 마크되지 않은 브랜드 요약 목록(최신순). 없으면 빈 배열 `[]` |
| `data.content[].id` | Long | 브랜드 식별자 |
| `data.content[].name` | String | 브랜드 이름 |
| `data.page` | Int | 현재 페이지 번호 (0부터) |
| `data.size` | Int | 페이지 크기 |
| `data.totalElements` | Long | 전체 브랜드 수 (삭제 마크 제외) |
| `data.totalPages` | Int | 전체 페이지 수 |

> `BrandFacade.getBrandsForAdmin` 은 `PageResult<AdminBrandResult>` 를 반환한다 — `data` 는 `content` + `page`/`size`/`totalElements`/`totalPages`.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |

---

## 3. (관리자) 브랜드 상세 조회

### Request
- `GET /api-admin/v1/brands/{brandId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `brandId` | Long | O | 조회 대상 브랜드 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":   7,
    "name": "나이키"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 브랜드 식별자 |
| `data.name` | String | 브랜드 이름 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `BRAND_NOT_FOUND` | 브랜드가 존재하지 않거나 삭제 마크됨 |

---

## 4. (관리자) 브랜드 등록

### Request
- `POST /api-admin/v1/brands`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "name": "나이키"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 브랜드 이름. 비어 있을 수 없다. 삭제 마크되지 않은 브랜드 사이에서 전체 유일 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":   7,
    "name": "나이키"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 등록된 브랜드 식별자 |
| `data.name` | String | 브랜드 이름 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BRAND_BAD_REQUEST` | 이름이 필수·형식 규칙을 어김 (`BrandName.of`) |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `409` | `DUPLICATE_BRAND_NAME` | 동일 이름의 브랜드가 이미 존재 |

---

## 5. (관리자) 브랜드 정보 수정

### Request
- `PUT /api-admin/v1/brands/{brandId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `brandId` | Long | O | 수정 대상 브랜드 식별자 |

**Request Body**
```jsonc
{
  "name": "나이키 코리아"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 변경할 브랜드 이름. 자기 자신을 제외하고 유일해야 한다 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":   7,
    "name": "나이키 코리아"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 브랜드 식별자 |
| `data.name` | String | 갱신된 브랜드 이름 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BRAND_BAD_REQUEST` | 변경 입력이 필수·형식 규칙을 어김 |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `BRAND_NOT_FOUND` | 브랜드가 존재하지 않거나 삭제 마크됨 |
| `409` | `DUPLICATE_BRAND_NAME` | 변경할 이름이 다른 브랜드와 중복 |

---

## 6. (관리자) 브랜드 삭제 — 카스케이드

### Request
- `DELETE /api-admin/v1/brands/{brandId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `brandId` | Long | O | 삭제 대상 브랜드 식별자 |

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
| `data` | null | 빈 페이로드. 브랜드와 그 소속 상품들이 **같은 트랜잭션** 안에서 함께 삭제 마크된다 (부분 적용 없음) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `BRAND_NOT_FOUND` | 브랜드가 존재하지 않거나 이미 삭제 마크됨 |

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `BRAND_BAD_REQUEST` | 400 | 브랜드 등록·수정 — 이름 형식 위반 (`BrandName.of`) |
| `UNAUTHORIZED` | 401 | 관리자 엔드포인트 — 관리자 인증 실패 (admin/logical-model §4) |
| `BRAND_NOT_FOUND` | 404 | 브랜드 조회·수정·삭제 — 미존재 또는 삭제 마크 (`BrandErrorType.BRAND_NOT_FOUND`) |
| `DUPLICATE_BRAND_NAME` | 409 | 브랜드 등록·수정 — 이름 중복 (`BrandErrorType.DUPLICATE_BRAND_NAME`) |

## 부록 — 구현 메모 / 미해결 사항

> 브랜드 컨트롤러(interfaces)는 아직 없다. 본 명세는 구현된 `application.brand.BrandFacade` 와 도메인 모델에 정합하도록 작성했다.

- **관리자 인증 (확정)**: [admin/logical-model.md](../admin/logical-model.md) 로 확정 — 별도 도메인 모델 없이 헤더 `X-Loopers-Ldap: loopers.admin` 으로 식별, `/api-admin/**` 경로 기준 인증. 컨트롤러 구현 시 `AdminAuthInterceptor`(§5) 를 따른다.
- **`description` 없음**: requirements 는 브랜드 "설명(설명 등)" 을 언급하나, 현재 `Brand` 도메인과 `BrandResult`/`AdminBrandResult` 는 `id`·`name` 만 가진다. 설명 필드 도입은 도메인 확장 시 결정.
- **회원/관리자 브랜드 응답 동일**: 현재 `BrandResult` 와 `AdminBrandResult` 는 `{id, name}` 으로 동일하다. 관리자 전용 정보(생성/수정 시각 등)가 필요해지면 분기.
- **목록 페이징**: `getBrandsForAdmin` 은 `PageResult<AdminBrandResult>` 를 반환한다 — 응답 `data` 는 `content` + `page`/`size`/`totalElements`/`totalPages`. 컨트롤러가 `Pageable → PageQuery` 로 변환한다(`like` 와 동일 패턴).
- **`PUT` vs `PATCH`**: 수정은 변경 가능한 필드(이름) 전체 치환이라 `PUT` 으로 표기했다.
- **HTTP 상태**: 등록 성공을 `200 OK` + 생성 리소스로 표기했다(프로젝트 컨벤션). `201 Created` 채택은 컨트롤러 구현 시 결정 가능.
