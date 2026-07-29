# 읽기 성능 — before/after 측정 기록

- 환경: MySQL 8.0.42 (docker `docker-mysql-1`), `products` 30만 건(293,875 alive, ~2% soft-delete), 100 브랜드
- 측정: `EXPLAIN ANALYZE` 의 actual time (쿼리 실제 실행). 워밍업 후 대표값. 페이지 크기 20.
- 인덱스 상태(baseline): `PRIMARY(id)` **단 하나** — 모든 조회가 풀스캔.

## Phase 1 — Baseline (인덱스 없음)

| # | 시나리오 | 접근(type) | 스캔 행 | 정렬 | actual time |
|---|----------|-----------|---------|------|-------------|
| 1 | 최신순 page0 | ALL(풀스캔) | 300,000 | filesort 293,875 | **118 ms** |
| 2 | 최신순 offset 100000 | ALL | 300,000 | filesort 100,020 | **180 ms** |
| 3 | 인기순 page0 | ALL | 300,000 | filesort 293,875 | **99 ms** |
| 4 | brand=7 + 가격↑ | ALL | 300,000 | filesort (필터후 2,884) | **69 ms** |
| 5 | brand=7 + 인기순 | ALL | 300,000 | filesort (필터후 2,884) | **69 ms** |
| 6a | count 전체 | ALL | 300,000 | - | **47 ms** |
| 6b | count brand=7 | ALL | 300,000 | - | **41 ms** |

### 공통 병목 (전 쿼리 동일 패턴)
- `Table scan on products` — LIMIT 20 을 받아도 **항상 30만 행 전부 읽음**. `key=NULL`.
- `Using filesort` — 정렬 인덱스가 없어 매번 정렬 연산. (4)(5) 는 brand 로 2,884건만 필요한데도 30만 풀스캔 후 정렬.
- `LIMIT/Offset 20/100000` — deep offset 은 버릴 10만 행까지 정렬 입력에 포함 → 페이지 깊어질수록 비용 증가.
- count 쿼리도 풀스캔. Spring Data `Page` 는 목록 + count 를 매 요청 동반.

> 클래식 EXPLAIN (5): `type=ALL, key=NULL, rows=298521, Extra: Using where; Using filesort`

### 해석
- 300만~1000만으로 늘거나 동시 요청이 쌓이면 위 ms 는 선형 이상으로 악화(버퍼풀 압박·정렬 메모리·CPU). 단건 69~180ms 도 목록 API 로는 이미 느림.
- 가장 손댈 가치가 큰 지점: **filter(brand_id) + sort(컬럼) 을 인덱스로 커버**해 (a) 풀스캔 제거 (b) filesort 제거.

## Phase 2 — 인덱스 설계 실험

페이지 20, brand_id=7(≈2,884 alive). 인덱스 1개씩 만들며 EXPLAIN.

### Decision 2 — leftmost prefix / 인덱스 개수
| 실험 | 인덱스 | 쿼리 | 결과 |
|---|---|---|---|
| A | (brand_id, like_count, id) | 전역 인기순 | `key=NULL, filesort` — **전역 정렬 커버 못함** |
| B | (brand_id, like_count, id) | brand=7 인기순 | `ref, Backward index scan`, rows=2938, filesort 없음 |
| C | (like_count, id) | 전역 인기순 | `index reverse, rows=20`, filesort 없음 |
| D | (like_count, id) only | brand=7 인기순 | filesort 없음이나 brand 찾으려 **1,936행 훑음 → 4.33ms** |
| E | (brand_id, like_count, id) | brand=7 인기순 | brand seek → **20행, 0.46ms** (≈9배) |

→ 전역 정렬엔 전역 인덱스, 브랜드 필터엔 brand 선두 인덱스가 별도로 필요. 전역 인덱스로 브랜드 필터를 대신하면 비용이 분포에 휘둘림.

### Decision 3 — ASC/DESC + id 타이브레이크
| 실험 | 인덱스 | 정렬 | filesort? |
|---|---|---|---|
| F | (brand_id, price, id) all-ASC | price ASC, **id DESC** (앱 현재) | **filesort 발생** (혼합 방향) |
| G | (brand_id, price, id) all-ASC | price ASC, **id ASC** | filesort 없음 |
| H | (brand_id, price ASC, **id DESC**) | price ASC, id DESC | filesort 없음 (전용 혼합 인덱스) |

→ price_asc 의 `id DESC` 타이브레이크가 혼합 방향을 만들어 filesort 유발. 타이브레이크를 주 정렬 방향과 통일(price면 id ASC)하면 평범한 오름차순 인덱스로 해결.

### Decision 1 — deleted_at 포함 여부
| 실험 | 인덱스 | 결과 |
|---|---|---|
| I | (brand_id, like_count, id) | deleted_at 은 `Using where` post-filter, 2% 삭제율이라 비용 무시 |
| J | (brand_id, like_count, id, deleted_at) | (I)과 플랜 동일, ICP 안 붙음 — 이득 없이 인덱스만 넓어짐 |
| K | (deleted_at, brand_id, like_count, id) | `ref=const,const` 동작하나 98% NULL near-constant 선두라 비대 |

## Phase 2 — 인덱스 적용 후 (확정 셋)

확정 인덱스(= `ProductEntity @Table(indexes)`, 평범한 오름차순 6개):
```
전역:   (created_at,id)  (price,id)  (like_count,id)
브랜드: (brand_id,created_at,id)  (brand_id,price,id)  (brand_id,like_count,id)
```
타이브레이크 방향 통일(`toJpaSort`): price_asc → id ASC. 모든 정렬을 정/역방향 스캔으로 커버, filesort 0.

| # | 시나리오 | before | after | 개선 | 사용 인덱스 / 비고 |
|---|----------|-------:|------:|-----:|---|
| 1 | 최신순 page0 | 118 ms | **0.065 ms** | ~1800x | idx_created_id (reverse), 22행만 |
| 2 | 최신순 offset 100000 | 180 ms | **120 ms** | 1.5x | 풀스캔/filesort 제거됐으나 **offset 10만행 여전히 walk** |
| 3 | 인기순 page0 | 99 ms | **0.028 ms** | ~3500x | idx_like_id (reverse), 22행만 |
| 4 | brand=7 + 가격↑ | 69 ms | **0.11 ms** | ~600x | idx_brand_price_id, **filesort 사라짐**(타이브레이크 수정 효과) |
| 5 | brand=7 + 인기순 | 69 ms | **0.089 ms** | ~770x | idx_brand_like_id (reverse), 20행 |
| 6b | count brand=7 | 41 ms | **1.9 ms** | ~22x | idx_brand_created_id 로 카운트 |

### 해석
- 필터+정렬+얕은 페이지: `type=ALL+filesort` → `ref/index + reverse scan` 으로 **풀스캔·filesort 동시 제거**, LIMIT 만큼(20여 행)만 읽음. 1000배 내외.
- (4) `price ASC, id ASC` 로 통일하니 평범한 `(brand_id, price, id)` 인덱스로 filesort 제거 확인.
- **(2) deep offset 만 여전히 느림(120ms)**: 인덱스로 순서는 잡혔지만 `LIMIT 20 OFFSET 100000` 은 버릴 10만 행을 인덱스에서 그대로 walk.

## Phase 3 — 정렬/필터 최적화 (커서 페이징)
> (작성 예정)

## Phase 5 — Redis 캐시

구조: `application.product.port.ProductCache`(outbound) ← `infrastructure.product.RedisProductCache`(어댑터). Facade read-through.

| 항목 | 상세(`/products/{id}`) | 목록(`/products`) |
|---|---|---|
| 키 | `product:detail:v1:{id}` | `product:list:v1:{brandId\|all}:{sort}:{page}:{size}` |
| 캐시 내용 | 공유 본문(product+brand), likedByMe 는 매 요청 합성 | `PageResult<ProductSummaryResult>` |
| 범위 | 전체 | `page < 5` 만 |
| TTL | 5분 | 60초 |
| 무효화 | TTL + 상품 수정/삭제 시 evict (좋아요는 TTL 감내) | TTL-only |
| 직렬화 | Jackson JSON(String value) | 〃 |

### 성능 비교 — HIT vs MISS (데이터 계층 실측)
MISS=DB 경로(EXPLAIN ANALYZE warm), HIT=Redis GET(redis-benchmark, 1.6KB 값).

| 경로 | MISS(DB) | HIT(Redis) | 개선 |
|---|---:|---:|---:|
| 목록 기본 핫키(필터X page0) | ~49 ms (find 0.024 + **count 49**) | ~0.10 ms | ~490x |
| 목록 브랜드 핫키(brand=7) | ~2.9 ms (find 0.09 + count 2.8) | ~0.10 ms | ~29x |
| 상세(PK 2회) | ~0.1 ms | ~0.10 ms | ~1x |

- 목록 MISS 는 `COUNT(*)`(deleted_at 전수 294k행 ~49ms)가 지배 → 캐시 HIT 가 count 자체를 생략.
- 상세는 PK 점조회라 지연 이득 ~1x, 가치는 DB 오프로드.
- 처리량: Redis GET 275,103 rps(c=50, p50 0.095ms) vs DB count(*) ~49ms/건. 동시성에서 비선형 격차.
- (주의: 데이터 계층 수치 — 앱 직렬화/네트워크 제외)

### 장애/미스 안전성
- 어댑터가 모든 Redis 호출을 `runCatching` 으로 감싸 **get=null(miss) / put·evict=no-op** 로 폴백 → Redis 다운에도 Facade 가 DB 로 정상 동작.

### 검증 (테스트)
- `RedisProductCacheTest` — Redis 예외 시 get=null, put/evict 무예외 (장애 흡수)
- `RedisProductCacheIntegrationTest` (Testcontainer) — 상세/목록 put→get 라운드트립, evict, 키 격리(다른 sort/brand=miss)
- `ProductFacadeTest` — 캐시 히트 시 DB 미조회, 미스 시 적재, page≥5 캐시 스킵, 수정/삭제 시 evict
- `ProductV1ApiE2ETest` — 캐시 활성 상태로 전 시나리오 통과(테스트 간 Redis flush)

### 로컬 캐시(L1) 보류
- 다중 인스턴스 정합성 비용(evict 전파에 Redis pub/sub 필요) + 상세 카디널리티 높아 적중률 낮음. 핫 목록 키 한정으로 측정 후 도입..

### 남은 정합성 트레이드오프
- 좋아요 수는 TTL(상세 5분/목록 60초) 내 stale 가능 — 인기 카운트는 근사 허용.
- 목록은 정밀 무효화 불가(어떤 페이지에 상품 X 가 있는지 모름) → TTL-only.
