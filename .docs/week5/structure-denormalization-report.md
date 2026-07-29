# 좋아요 수 정렬 — 정규화 vs 비정규화 구조 비교 보고서

> **TL;DR** — "좋아요 순 정렬"을 정규화(`JOIN + GROUP BY + ORDER BY COUNT`)로 풀면 인덱스를 줘도 전역 인기순이 797ms 다. 상위 N개를 알려면 전체를 집계·정렬해야 해 `LIMIT` 가 무력하기 때문이다. 미리 집계해 둔 `like_count` 컬럼(비정규화) + 정렬 인덱스는 같은 조회를 0.047ms 에 끝낸다(약 17,000배). 쓰기 비용·드리프트라는 대가는 원자 증감과 (차주)재계산 배치로 통제한다.

---

## 1. 개요 — 왜 "역실험"인가

본 프로젝트는 처음부터 `products.like_count` 컬럼을 비정규화해 두었다. 그래서 과제 ②("비정규화로 좋아요 순 정렬 개선")의 *구조*는 이미 충족돼 있었으나, **정규화 대비 얼마나 빠른지에 대한 before/after 수치가 없었다.**

이를 메우기 위해 **역실험**을 수행했다. `likes` 원장을 현실 규모로 채운 뒤, 같은 "좋아요 순 정렬"을 세 구조(정규화·정규화+인덱스·비정규화)로 측정해 비교한다. 운영 코드는 비정규화 그대로 두고, 정규화 쿼리는 측정 목적으로만 실행했다.

---

## 2. 실험 환경

| 항목 | 값 |
|---|---|
| `products` | 30만 건 (alive 293,875) |
| `likes` | 약 200만 건 (`sql/seed_likes.sql`), 289,732개 상품에 분포, 한 상품 최대 12,491 likes |
| `likes` 분포 | user 30만 분산 + product 좌편향(`POW(RAND(),2.5)`) → 인기 집중 |
| 측정법 | `EXPLAIN ANALYZE` actual time, `LIMIT 20` |

> `products.like_count`(독립 시드)와 `likes` 행 수는 값이 일치하지 않는다. 본 실험은 "값 일치"가 아니라 **쿼리 구조의 비용**을 비교한다.

---

## 3. 측정 결과 — 3-way

같은 "좋아요 순 정렬 `LIMIT 20`" 을 세 구조로 측정했다.

| # | 구조 | 전역 인기순 | 브랜드(7) 인기순 |
|---|---|---:|---:|
| AS-IS① | 정규화 (JOIN + GROUP BY, `likes` 인덱스 없음) | 2,306 ms | 493 ms |
| AS-IS② | 정규화 + `likes(product_id)` 인덱스 | 797 ms | 20.4 ms |
| TO-BE | 비정규화 (`like_count` 컬럼 + 복합 인덱스) | 0.047 ms | 0.219 ms |

**비정규화 개선폭** — 전역 약 17,000배(vs ②) / 약 49,000배(vs ①), 브랜드 약 93배(vs ②) / 약 2,250배(vs ①).

```text
AS-IS① (전역) — likes 전수를 hash join 후 임시테이블 집계 + 정렬
-> Sort: cnt DESC  (actual time=2306..2306)
  -> Aggregate using temporary table  (rows=293874 groups)
    -> Left hash join (l.product_id = p.id)  (actual rows=1.97e6)
        -> Table scan on p (300000)
        -> Hash → Covering index scan on l (2e6 전부 읽어 해시 구성)

AS-IS② (전역) — 인덱스로 join 은 빨라졌으나, 여전히 전 상품 순회 집계 + 정렬
-> Sort: cnt DESC  (actual time=797..797)
  -> Group aggregate: count(l.id)  (rows=293875)
    -> Nested loop left join  (actual rows=1.97e6, loops=293875)

TO-BE (전역) — 정렬 인덱스를 역방향으로 20개만 읽고 종료
-> Limit: 20  (actual time=0.0166..0.0471)
  -> Index scan on products using idx_products_like_id (reverse)  (rows=22)
```

---

## 4. 해석 — 왜 정규화는 인덱스로도 못 구하나

1. **정규화는 `LIMIT 20` 을 살리지 못한다.** 상위 20개를 알려면 *모든 상품의 좋아요를 세어(GROUP BY)* 정렬해야 한다. 전역은 293,875개 그룹 전부를 집계·정렬한 뒤에야 20개를 자른다. `LIMIT` 가 스캔을 조기 종료시키지 못한다.
2. **`likes(product_id)` 인덱스를 줘도(②) 전역은 797ms.** 인덱스는 조인 방식을 hash join → nested loop 로 개선할 뿐, **전 상품 순회 + 집계 + 정렬**이라는 본질을 없애지 못한다. (참고로 `likes` 의 유니크 키는 `(user_id, product_id)` 라 `product_id` 가 두 번째 컬럼이고, leftmost prefix 상 조인에 쓸 수 없어 ①에서는 전용 인덱스가 아예 없었다.)
3. **비정규화는 `LIMIT 20` 을 산다.** `like_count` 가 이미 정렬돼 있어 인덱스를 역방향으로 20개만 읽고 끝난다. 조인·group by·임시테이블이 통째로 사라진다.
4. 브랜드 필터는 집계 대상이 2,884개로 줄어 정규화②도 20ms 로 개선되지만, 여전히 비정규화(0.2ms)의 약 90배다.

> 핵심: "좋아요 순 정렬"은 **집계와 정렬을 `LIMIT` 이전에 끝내야 하는** 형태라, 정규화로는 인덱스를 줘도 전수 집계를 피할 수 없다. 비정규화는 집계 결과를 컬럼에 미리 굳혀(pre-aggregation) `LIMIT` 를 인덱스로 살린다.

---

## 5. 비정규화의 대가 (trade-off)

빠른 읽기는 공짜가 아니다. 비정규화는 읽기를 위해 쓰기 일관성을 일부 떠안는 거래다.

| 비용 | 내용 | 현재 대응 |
|---|---|---|
| 쓰기 비용 | 좋아요마다 `like_count` UPDATE + 이를 포함한 인덱스 2종 갱신 | 좋아요 시 캐시 evict 안 함, 인덱스 6종 중 2종만 like_count 포함 |
| 동시성 | 동시 좋아요/취소 시 카운트 정확성 | 원자 증감(`+1/-1`) + 단일 DELETE 게이트, 동시성 4종 테스트 통과 |
| 정합성 드리프트 | 장기적으로 `like_count ≠ COUNT(likes)` 가능 | 재계산 배치로 사후 교정 (차주, `commerce-batch`) |

읽기 약 17,000배 개선이 위 쓰기 비용을 압도한다. "읽기 지배 + 인기순이 핵심 정렬"인 커머스에서는 비정규화가 합리적 선택이다.

---

## 6. 대안 — Materialized View / 조회 전용 모델

`like_count` 컬럼 대신 **조회 전용 집계 구조**(예: `product_like_view(product_id, like_count)`)를 배치/이벤트로 적재하는 방식도 같은 "미리 집계" 전략이다. 원본 `products` 의 쓰기 경로를 건드리지 않는 대신 실시간성을 더 내주며, 쓰기 모델과 읽기 모델을 분리하는 CQRS 의 읽기 측 최적화에 해당한다.

| 방식 | 장점 | 단점 |
|---|---|---|
| 컬럼 비정규화 (채택) | 단순, 추가 동기화 로직 없음, 즉시 일관 | `products` 쓰기 경로에 카운트 갱신이 얹힘 |
| 조회 전용 테이블/MV | 원본 쓰기 경로 무간섭, 튜닝 자유 | 별도 적재 파이프라인, 실시간성 희생 |

본 프로젝트는 단순성을 위해 컬럼 비정규화를 택했다. 좋아요 쓰기가 `products` 핫 경로에 부담을 주거나 집계 차원이 늘어나면 조회 전용 모델로 분리하는 것이 다음 수순이다.

---

## 7. 결론

- **좋아요 순 정렬은 정규화로 인덱스를 줘도 못 구한다**(전역 797ms). 전수 집계 + 정렬이 본질이기 때문이다.
- **비정규화 `like_count` + 정렬 인덱스**가 `LIMIT` 를 살려 0.047ms 에 끝낸다(약 17,000배).
- 대가(쓰기·드리프트)는 원자 증감·동시성 테스트·(차주)재계산 배치로 관리 가능한 수준이며, 규모가 커지면 조회 전용 모델로 확장한다.

---

## 8. 재현

```bash
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < .docs/week5/sql/seed_likes.sql
```
```sql
-- AS-IS① : likes(product_id) 인덱스 없이 JOIN + GROUP BY 측정
SELECT p.id, COUNT(l.id) cnt FROM products p
  LEFT JOIN likes l ON l.product_id = p.id
  WHERE p.deleted_at IS NULL
  GROUP BY p.id ORDER BY cnt DESC, p.id DESC LIMIT 20;

-- AS-IS② : CREATE INDEX idx_likes_product ON likes(product_id) 후 위 쿼리 재측정

-- TO-BE  : 비정규화 컬럼 정렬
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC, id DESC LIMIT 20;
```
