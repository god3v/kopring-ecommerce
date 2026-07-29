# 랭킹(Ranking) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [docs/domain/ranking/requirements.md](./requirements.md)

---

## 0. 공통 사항

### 0.1 인증 — 불필요

랭킹 조회는 인증 없이 접근 가능한 공개 지면이다. 상품 상세 확장(§2)만 기존 상품 명세의 선택 인증을 따른다.

### 0.2 날짜 표기

- 날짜는 `yyyyMMdd` 문자열, 시간대는 `Asia/Seoul` 기준이다.
- 랭킹판의 보존 기간은 2일이다 — 사실상 오늘과 어제만 조회 가능하며, 그 밖의 날짜는 빈 랭킹으로 응답한다.

### 0.3 점수의 성격

- `score` 는 행동 신호(조회·좋아요·주문)에 가중치를 곱해 누적한 **근사값**이다. 회계적 정확성을 보장하지 않으며, 상대 순위의 유의미함만 보장한다.
- 행동이 랭킹에 반영되기까지 이벤트 소비 지연(초 단위)이 있을 수 있다.

### 0.4 엔드포인트 일람

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| `GET` | `/api/v1/rankings` | 랭킹 페이지 조회 | 불필요 |
| `GET` | `/api/v1/products/{productId}` | 상품 상세 조회 — 순위 필드 확장 (§2) | 선택 |

---

## 1. 랭킹 페이지 조회

### Request
- `GET /api/v1/rankings?date={date}&page={page}&size={size}`
- **인증**: 불필요
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 예시 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|---|
| `date` | `20260713` | String | X | 오늘(Asia/Seoul) | `yyyyMMdd` 형식. 형식 위반은 거부. 보존 기간 밖 날짜는 빈 목록 |
| `page` | `0` | Int | X | `0` | 페이지 번호. 0부터 시작, 음수는 `0` 으로 보정 |
| `size` | `20` | Int | X | `20` | 페이지당 항목 수. 상한 초과 시 상한값으로 보정 |

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
        "rank":      1,
        "score":     123.4,
        "productId": 101,
        "name":      "운동화",
        "price":     59000,
        "brandName": "나이키",
        "likeCount": 42
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 87,
    "totalPages":    5
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content` | Array | 점수 내림차순 랭킹 항목 목록. 랭킹판이 없거나 비어 있으면 빈 배열 `[]` |
| `data.content[].rank` | Int | 순위. 1부터 시작 |
| `data.content[].score` | Double | 가중 누적 점수 (근사값, `§0.3`) |
| `data.content[].productId` | Long | 상품 식별자 |
| `data.content[].name` | String | 상품 이름 |
| `data.content[].price` | Long | 상품 가격 |
| `data.content[].brandName` | String | 브랜드 이름 |
| `data.content[].likeCount` | Long | 좋아요 수 |
| `data.page` | Int | 현재 페이지 번호 (0부터) |
| `data.size` | Int | 페이지 크기 |
| `data.totalElements` | Long | 랭킹판 전체 항목 수 |
| `data.totalPages` | Int | 전체 페이지 수 |

> 삭제된 상품은 목록에서 제외된다. `totalElements` 는 랭킹판 기준이므로, 제외가 발생한 페이지는 반환 항목 수가 `size` 보다 적을 수 있다.

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `RANKING_BAD_REQUEST` | `date` 가 `yyyyMMdd` 형식이 아님 |
| `500` | `INTERNAL_ERROR` | 집계 저장소 접근 불가 — 빈 목록으로 위장하지 않고 오류를 드러낸다 (requirements §4.1 E3) |

> 보존 기간 밖 날짜·집계 이전 날짜는 실패가 아니라 **빈 목록 정상 응답**이다 (requirements §4.1 E2).

---

## 2. 상품 상세 조회 — 순위 필드 확장

기존 [상품 상세 조회](../product/api-spec.md) `§2` 를 확장한다. 요청 계약은 동일하고, 응답에 `rank` 필드가 추가된다.

### Request
- `GET /api/v1/products/{productId}`
- **인증**: 선택 — 상품 명세 `§0.1` 과 동일. 인증 여부는 `likedByMe` 에만 영향을 준다
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 조회 대상 상품 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":        101,
    "name":      "운동화",
    "price":     59000,
    "likeCount": 42,
    "brandId":   7,
    "brandName": "나이키",
    "likedByMe": false,
    "rank":      3
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.rank` | Int? | **(신규)** 오늘(Asia/Seoul) 랭킹판 기준 순위, 1부터. 랭킹판에 없으면 `null` |
| 나머지 필드 | — | 상품 명세 `§2` 와 동일 |

> 순위를 확인할 수 없는 상황(집계 저장소 접근 불가)에서도 상세 조회는 실패하지 않는다 — `rank` 만 `null` 로 반환된다 (requirements §4.2 E2).

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `404` | `PRODUCT_NOT_FOUND` | 상품이 존재하지 않거나 삭제 마크됨 (기존과 동일) |

> 순위 조회 실패는 실패 응답 사유가 아니다 — 폴백(`rank: null`)으로 흡수된다.

---

## 부록 — 응답 ErrorType 정리

| errorCode | HTTP | 발생 지점 |
|---|---|---|
| `RANKING_BAD_REQUEST` | 400 | 랭킹 조회의 `date` 형식 위반 (신규 도메인 코드) |
| `PRODUCT_NOT_FOUND` | 404 | 상품 상세 조회 (기존 상품 도메인 코드) |
| `INTERNAL_ERROR` | 500 | 랭킹 페이지 조회 중 집계 저장소 접근 불가 |
