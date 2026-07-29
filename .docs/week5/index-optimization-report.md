# 상품 목록 조회 인덱스 최적화 보고서

> **TL;DR** — 인덱스 없는 목록 조회는 30만 건 풀스캔 + filesort 로 매 요청 수십~수백 ms 를 쓴다. 필터·정렬 유즈케이스별 복합 인덱스 6종을 `필터 → 정렬 → 타이브레이크` 순으로 설계해 풀스캔과 filesort 를 동시에 제거하고 핫 경로를 약 1,000배 이상 단축했다. 타이브레이크 방향을 주 정렬과 통일해 내림차순/혼합 인덱스 없이 평범한 오름차순 인덱스만으로 모든 정렬을 커버한다.

---

## 1. 개요

### 대상 API
- `GET /api/v1/products` — 상품 목록 조회 (브랜드 필터 + 정렬 + 페이징)

### 목표
- 느린 목록 조회의 병목을 `EXPLAIN` 으로 규명
- 필터·정렬 유즈케이스별 인덱스를 설계하고 전후 성능을 수치로 비교
- 인덱스가 해결하는 영역과 해결하지 못하는 영역(deep offset)을 구분

---

## 2. 측정 환경

| 항목 | 값 |
|---|---|
| DB | MySQL 8.0.42 (Docker) |
| 데이터 | `products` 30만 건 (alive 293,875 / soft-delete 약 2%) |
| 브랜드 수 | 100 (브랜드당 평균 약 3,000건) |
| 분포 | price 1천~100만, like_count 좌편향(`POW(RAND(),3)`), created_at 최근 2년 랜덤 |
| 측정법 | `EXPLAIN ANALYZE` actual time, 페이지 크기 20 |
| 스크립트 | `sql/seed.sql`, `sql/baseline.sql`, `sql/after_index.sql` |

> 인덱스 효과는 데이터 분포에 민감하다. 카디널리티가 다양하도록 분산 시드해, 옵티마이저가 실제 운영과 유사한 선택을 하도록 했다.

---

## 3. 조회 유즈케이스 분석

API 파라미터는 `brandId`(옵션), `sort`(latest/price_asc/likes_desc), `page/size` 이고, soft-delete 정책상 모든 쿼리에 `deleted_at IS NULL` 이 붙는다(`@SQLRestriction`). 필터 유무 2 × 정렬 3 = 6 유즈케이스다.

| # | 필터 | 정렬 | 의미 |
|---|---|---|---|
| U1 | 없음 | created_at DESC | 전체 최신순 (기본/홈) |
| U2 | 없음 | price ASC | 전체 가격 낮은 순 |
| U3 | 없음 | like_count DESC | 인기 상품 |
| U4 | brand_id | created_at DESC | 브랜드 신상 |
| U5 | brand_id | price ASC | 브랜드 가격순 |
| U6 | brand_id | like_count DESC | 브랜드 인기순 (과제 핵심) |

모든 정렬에는 동점 처리용 타이브레이크 `id` 가 붙어 페이지 간 중복/누락 없는 결정적 순서를 보장한다. 페이징은 offset 방식이다.

---

## 4. 병목 분석 (인덱스 없는 baseline)

기존 인덱스는 `PRIMARY(id)` 뿐이었다. 6 유즈케이스 모두 동일한 패턴으로 느렸다.

```text
EXPLAIN (U6, brand=7 + 인기순)
type=ALL, key=NULL, rows=298521, Extra: Using where; Using filesort
```
```text
EXPLAIN ANALYZE (U1 최신순)
-> Limit: 20  (actual time=118..118)
  -> Sort: created_at DESC, id DESC  (actual time=118..118)        # filesort
    -> Filter: deleted_at is null  (rows=293875)
      -> Table scan on products  (rows=300000)                     # 풀스캔
```

| 유즈케이스 | type | 스캔 | 정렬 | actual time |
|---|---|---:|---|---:|
| U1 최신순 page0 | ALL | 300,000 | filesort 293,875 | 118 ms |
| U1 최신순 offset 100000 | ALL | 300,000 | filesort 100,020 | 180 ms |
| U3 인기순 page0 | ALL | 300,000 | filesort 293,875 | 99 ms |
| U5 brand=7 가격↑ | ALL | 300,000 | filesort (필터 후 2,884) | 69 ms |
| U6 brand=7 인기순 | ALL | 300,000 | filesort (필터 후 2,884) | 69 ms |

병목은 네 가지다.

1. **풀스캔** (`type=ALL`, `key=NULL`) — `LIMIT 20` 이어도 상위 20개를 알려면 정렬을 끝내야 하므로 30만 행을 전부 읽는다. 브랜드 필터(U5·U6)조차 `brand_id` 인덱스가 없어 30만 행을 읽은 뒤 메모리에서 거른다.
2. **filesort** (`Using filesort`) — 정렬 인덱스가 없어 매 요청 정렬 연산을 수행한다.
3. **deep offset** — `OFFSET 100000` 은 버릴 10만 행까지 정렬 입력에 포함되어, 페이지가 깊을수록 비용이 누적된다.
4. **count 동반** — Spring Data `Page` 는 목록과 `COUNT(*)` 를 함께 던지며, 이 count 역시 풀스캔이다.

---

## 5. 인덱스 설계 — 결정과 근거

설계 공식은 **`등치 필터(brand_id) → 정렬 컬럼 → 타이브레이크(id)`** 순이다. 등치로 한 점에 도달한 뒤 그 안에서 정렬 컬럼이 이미 정렬돼 있으면, 인덱스를 따라 `LIMIT` 만큼만 읽으면 된다(풀스캔·filesort 동시 제거). 세 가지 결정을 `EXPLAIN` 실험으로 검증했다.

### 5.1 전역/브랜드 인덱스를 각각 둔다 — leftmost prefix
복합 인덱스는 **왼쪽 컬럼부터** 정렬된 B-Tree 다. 첫 컬럼을 건너뛴 탐색·정렬은 불가능하다.

| 실험 | 인덱스 | 쿼리 | 결과 |
|---|---|---|---|
| A | (brand_id, like_count, id) | 전역 인기순 | `key=NULL, filesort` — 전역 정렬 커버 못 함 |
| B | (brand_id, like_count, id) | brand=7 인기순 | `ref` + backward index scan, filesort 없음 |
| C | (like_count, id) | 전역 인기순 | `index` reverse, 20행, filesort 없음 |
| D | (like_count, id) 만 | brand=7 인기순 | filesort 는 없으나 brand 찾으려 1,936행 walk → 4.33 ms |
| E | (brand_id, like_count, id) | brand=7 인기순 | brand seek → 20행, 0.46 ms |

전역 정렬에는 정렬 컬럼이 선두인 인덱스가, 브랜드 필터에는 `brand_id` 가 선두인 인덱스가 각각 필요하다(A vs C). 전역 인덱스로 브랜드 필터를 대신하면(D) 원하는 브랜드를 만날 때까지 인덱스를 훑어, 비용이 데이터 분포에 휘둘린다. 따라서 정렬 3 × 필터 유무 2 = **6개**.

### 5.2 타이브레이크 방향을 주 정렬과 통일
인덱스는 한 방향(오름차순)으로 저장되지만, MySQL 8.0 은 이를 **정/역 양방향으로 스캔**할 수 있다(backward index scan). 따라서 같은 방향 정렬은 평범한 오름차순 인덱스로 커버된다. 문제는 주 정렬과 타이브레이크의 방향이 **엇갈릴 때**다.

| 실험 | 인덱스 | 정렬 | filesort? |
|---|---|---|---|
| F | (brand_id, price, id) | price ASC, **id DESC** (기존) | 발생 (혼합 방향) |
| G | (brand_id, price, id) | price ASC, **id ASC** | 없음 |
| H | (brand_id, price ASC, id DESC) | price ASC, id DESC | 없음 (전용 혼합 인덱스 필요) |

기존 `toJpaSort` 는 모든 정렬에 `id DESC` 를 붙였는데, `price ASC` 와 만나면 혼합 방향이 되어 한 번의 스캔으로 못 맞춘다(F). 타이브레이크를 주 정렬과 통일(`price_asc → id ASC`)하면(G) 평범한 인덱스로 해결된다. 이 결정 덕에 **내림차순/혼합 전용 인덱스가 전혀 필요 없어진다** — 내림차순 정렬은 backward scan 으로 처리된다. 동점 상품 간 순서는 사용자에게 의미가 없어, 인덱스 친화적 방향을 택한 것이다.

### 5.3 `deleted_at` 은 인덱스에서 제외 — 선택도
`deleted_at IS NULL` 은 모든 쿼리에 붙지만 **선택도(selectivity)가 거의 없다**(약 98% NULL).

| 실험 | 인덱스 | 결과 |
|---|---|---|
| I | (brand_id, like_count, id) | `deleted_at` 은 `Using where` post-filter, 2% 삭제율이라 비용 무시 |
| J | (…, id, deleted_at) | I 와 동일 플랜, ICP(Index Condition Pushdown)도 붙지 않음 — 이득 없이 인덱스만 비대 |
| K | (deleted_at, …) | 동작은 하나 98% NULL 인 near-constant 가 선두라 비효율 |

선두에 두면 거의 전부를 가리키는 무의미한 출발점이 되고, 뒤에 붙여도 이득이 없다. 가져온 행에서 `deleted_at` 만 마지막에 확인하는 post-filter 가 LIMIT 20 + 삭제율 2% 에서 사실상 공짜다. 따라서 제외한다. (삭제율이 커지면 재고. MySQL 은 PostgreSQL 의 부분 인덱스를 지원하지 않아 `WHERE deleted_at IS NULL` 조건부 인덱스라는 대안도 없다.)

---

## 6. 최종 인덱스 셋

`ProductEntity @Table(indexes=...)` 로 선언하며, 모두 평범한 오름차순이다.

```text
전역:   (created_at, id)            (price, id)            (like_count, id)
브랜드: (brand_id, created_at, id)  (brand_id, price, id)  (brand_id, like_count, id)
```

| 인덱스 | 커버 유즈케이스 | 스캔 방향 |
|---|---|---|
| idx_products_created_id | U1 | 역방향 |
| idx_products_price_id | U2 | 정방향 |
| idx_products_like_id | U3 | 역방향 |
| idx_products_brand_created_id | U4 | 역방향 |
| idx_products_brand_price_id | U5 | 정방향 |
| idx_products_brand_like_id | U6 | 역방향 |

---

## 7. 성능 비교 (before / after)

```text
EXPLAIN ANALYZE (U1 after)
-> Limit: 20  (actual time=0.026..0.065)
  -> Filter: deleted_at is null
    -> Index scan on products using idx_products_created_id (reverse)  (rows=22)
```

| 유즈케이스 | before | after | 개선 | after 플랜 |
|---|---:|---:|---:|---|
| U1 최신순 page0 | 118 ms | 0.065 ms | 약 1,800배 | idx_created_id reverse, 22행 |
| U1 최신순 offset 100000 | 180 ms | 120 ms | 1.5배 | 풀스캔/filesort 제거, **offset 10만행 walk 잔존** |
| U3 인기순 page0 | 99 ms | 0.028 ms | 약 3,500배 | idx_like_id reverse, 22행 |
| U5 brand=7 가격↑ | 69 ms | 0.11 ms | 약 600배 | idx_brand_price_id, **filesort 제거**(타이브레이크 효과) |
| U6 brand=7 인기순 | 69 ms | 0.089 ms | 약 770배 | idx_brand_like_id reverse, 20행 |
| count brand=7 | 41 ms | 1.9 ms | 약 22배 | idx_brand_created_id 로 카운트 |

필터+정렬+얕은 페이지는 `type=ALL + filesort` 에서 `ref/index + reverse scan` 으로 바뀌며 풀스캔과 filesort 가 동시에 사라지고, `LIMIT` 만큼(약 20행)만 읽어 1,000배 안팎 개선됐다. U5 에서는 타이브레이크 통일만으로 filesort 가 제거됨을 실측으로 확인했다.

> **deep offset(120ms)은 인덱스로 해결되지 않는다.** 순서는 인덱스로 잡혔으나, `OFFSET 100000` 은 버릴 10만 행을 인덱스에서 그대로 walk 한다. 이는 인덱스의 한계이며 커서(keyset) 페이징으로 별도 해결해야 한다.

---

## 8. 트레이드오프와 남은 과제

- **write amplification** — `like_count` 는 좋아요마다 바뀌는 핫 컬럼인데, 이를 포함한 인덱스 2종(`(like_count,id)`, `(brand_id,like_count,id)`)은 좋아요 한 번마다 함께 갱신된다. 읽기 성능의 대가로 쓰기·인덱스 유지비를 낸다. 인기 목록 캐시로 읽기 부하를 추가로 덜어 비용 대비 효과를 맞춘다.
- **인덱스 6개의 저장·쓰기 비용** — 현재는 모든 노출 조합을 커버하는 풀세트로 두되, 사용 빈도 측정이 쌓이면 저빈도 인덱스를 제거한다.
- **deep offset** — 커서(keyset) 페이징으로 `OFFSET` 비용과 `COUNT(*)` 동반을 함께 제거하는 것이 다음 단계다.
- **covering index** — 현재 `SELECT *` 라 PK 룩업이 따른다. 응답 컬럼이 고정되면 정렬 인덱스에 필요한 컬럼을 포함해 `Using index`(커버링)로 룩업까지 없앨 여지가 있다.
