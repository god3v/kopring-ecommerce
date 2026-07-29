# 랭킹(Ranking) 논리 모델 — ZSET 키 규약

> **상태**: v0.1 초안
> **작성일**: 2026-07-13
> 요구사항: [requirements.md](./requirements.md) §5 가 데이터 구조 결정을 본 문서로 위임한다.
> 랭킹판을 **쓰는 쪽(commerce-streamer)** 과 **읽는 쪽(commerce-api)** 이 공유하는 와이어 계약이다. 코드로 강제되지 않으므로 본 문서가 규약의 단일 출처다.

---

## 1. 저장소 선택 — Redis Sorted Set

| 요구 | ZSET 이 제공하는 것 |
|---|---|
| 점수 내림차순 Top-N | 정렬이 자료구조에 내장 — `ZREVRANGE`, O(N) |
| 실시간 점수 누적 | `ZINCRBY` 원자 증분, O(logN) |
| 개별 상품 순위 | `ZREVRANK`, O(logN) |
| 시간 창 리셋·만료 | 날짜별 키 분리 + `EXPIRE` |

DB `GROUP BY + ORDER BY` 는 데이터 증가에 따라 느려지고 고빈도 조회가 원천 DB 를 압박한다. 정렬·순위·증분이 모두 필요한 랭킹에는 ZSET 이 맞다.

## 2. 키 규약

```
rank:all:{yyyyMMdd}          예: rank:all:20260713
```

| 항목 | 규약 | 근거 |
|---|---|---|
| 키 패턴 | `rank:all:{yyyyMMdd}` | 일간 시간 창. 날짜마다 새 랭킹판 |
| `all` 자리 | 랭킹 범위(scope). 이번 범위는 전체(`all`) 고정 | 카테고리/브랜드별 랭킹 확장 예약 |
| 날짜 | 행동이 일어난 시각(`occurredAt`)의 **Asia/Seoul 날짜** | 늦게 도착한 이벤트도 행동 발생일에 귀속. API `date` 의미와 일치 |
| member | `productId` 십진 문자열 (예: `"101"`) | 랭킹판은 상품 단위 집계만 담는다 — 유저 정보 없음 |
| score | 가중 누적 점수, 배정밀도 실수. **음수 허용** | 좋아요 취소 차감이 시간 창을 넘으면 음수 가능 (requirements §5) |
| TTL | **48시간** — 점수 누적 시마다 갱신 | 시간 창(1일)의 2배. 보존 기간 = 오늘 + 어제 |

### 멱등 표식 키

```
rank:seen:{eventId}:{productId}   예: rank:seen:8f14e45f-...:101
```

| 항목 | 규약 |
|---|---|
| 타입 | String (값은 의미 없음, 존재 여부만 사용) |
| 멱등 단위 | (이벤트, 상품) — 한 주문(같은 eventId)의 여러 상품 라인이 서로를 막지 않도록 productId 를 키에 포함한다 |
| TTL | 48시간 — 재전달이 도달할 수 있는 기간보다 충분히 길게, 랭킹판 보존 기간과 동일 |
| 용도 | 랭킹 반영의 exactly-once — 표식 생성(SETNX)과 점수 증분(ZINCRBY)을 Lua 로 원자 실행 |

### 예약 (범위 밖, 확장 시 이 문서에 추가)

```
rank:all:{yyyyMMddHH}        시간 단위 랭킹 (TTL 축소)
rank:brand:{brandId}:{yyyyMMdd} 등 scope 확장
```

## 3. 연산 매핑

| 목적 | 연산 | 수행 주체 |
|---|---|---|
| 점수 누적 (조회·좋아요·주문) | **Lua 원자 실행**: `SETNX rank:seen:{eventId}:{productId}` 가 1이면 `ZINCRBY key delta productId` + `EXPIRE`(둘 다) | commerce-streamer, **랭킹 전용 consumer group** |
| 주문 신호의 원천 | `ORDER_PAID`(결제 확정) 이벤트 — `ORDER_CREATED` 는 내부 이벤트라 와이어에 나오지 않는다 | commerce-api (발행) |
| 좋아요 취소 차감 | 같은 Lua 경로에 음수 delta | commerce-streamer (랭킹 그룹) |
| 삭제 상품 정리 | `PRODUCT_DELETED` 소비 → 보존 기간에서 도출한 살아 있는 일간 판 전체(+이월로 미리 만든 내일 판)에서 `ZREM productId` | commerce-streamer (랭킹 그룹) |
| Top-N 페이지 | `ZREVRANGE key start end WITHSCORES` — `start = page × size` | commerce-api (읽기) |
| 개별 순위 | `ZREVRANK key productId` — 0-based 반환값에 **+1 해 1-based 로 노출** | commerce-api (읽기) |
| 전체 항목 수 | `ZCARD key` | commerce-api (읽기) |
| 이월 (Carry-Over) | `ZUNIONSTORE {내일키} 1 {오늘키} WEIGHTS 0.1` — 23:50 실행, **목적지 키 존재 시 스킵**(중복 실행·자정 후 오발동 방어) | commerce-streamer (스케줄러) |

- 순위는 `ZREVRANGE` 의 페이지 시작 인덱스에서 이어진다 — `rank = page × size + (목록 내 위치) + 1`.
- 쓰기·읽기 모두 master 템플릿을 쓴다 — 대기열 ZSET 조회와 같은 이유로, 갱신 직후에도 순위가 흔들리지 않게 최신 상태를 읽는다. replica 분산은 복제 지연이 순위 정확성을 흐리므로 채택하지 않는다.
- 이월은 23:50 스냅샷 복사다 — 23:50~자정 사이에 쌓인 점수는 다음 날 이월분에 포함되지 않는다(허용).

## 4. 정합성 — 전용 consumer group + Lua 원자 멱등

행동 이벤트는 At-Least-Once 로 전달되고 `ZINCRBY` 는 멱등이 아니다. 랭킹은 **상품 지표 집계(DB)와 별도의 consumer group** 으로 같은 토픽을 독립 소비하고, 멱등 표식을 DB 가 아니라 **Redis 에** 둔다 — 표식과 증분이 같은 저장소에 있어 원자화가 가능해진다.

```lua
-- 하나의 Lua 스크립트로 원자 실행 (둘 다 되거나, 둘 다 안 되거나)
if redis.call('SETNX', seenKey, '1') == 1 then
    redis.call('EXPIRE', seenKey, 172800)
    redis.call('ZINCRBY', rankKey, delta, productId)
    redis.call('EXPIRE', rankKey, 172800)
end
```

- **중복 없음**: 재전달은 `SETNX` 가 걸러 증분에 도달하지 않는다.
- **유실 없음**: 표식만 남고 증분이 빠지는 상태가 원자성 때문에 존재할 수 없다. ack 전에 죽으면 재전달 → 처음부터 다시 → 정확히 1회 수렴.
- **장애 = 지연**: Redis 접근 불가 시 예외를 전파해 배치 재전달로 복구한다(컨슈머 랙으로 관측). metrics 그룹과 오프셋이 독립이라 서로 영향 없다. DLT 격리는 형식 불량(malformed) 메시지만.
- 남는 위험은 Redis 자체의 데이터 유실(영속성 설정·장비 사고)뿐 — 랭킹은 일간 리셋이라 감수한다.
- ZSET 쓰기를 DB 트랜잭션 안에 넣지 않는다 — Redis 는 롤백에 참여하지 않는다(이 원칙은 유지).

## 5. 메모리 관측 기준

- 랭킹판: 항목당 대략 수십 바이트(member 문자열 + score + skiplist 오버헤드) × 일일 활동 상품 수 × 보존 2일.
- 멱등 표식(`rank:seen:*`): 키당 ~100B × 일일 이벤트 수 × 2일 — 이벤트 100만 건/일이어도 수백 MB 미만이나, 랭킹판보다 이쪽이 먼저 커진다. 이벤트 규모가 커지면 표식 TTL 단축(재전달 창 기준 재산정)이 첫 번째 손잡이.
- 상품 10만 개 전부가 매일 활동해도 랭킹판은 수십 MB 수준 — 당장 상위 N 절삭은 하지 않는다. 절삭(`ZREMRANGEBYRANK`)은 메모리가 문제 될 때의 개선 후보로 남긴다.
