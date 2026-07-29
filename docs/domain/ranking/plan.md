성# ranking 도메인 — TDD 플랜

> **입력 명세**: [requirements.md](./requirements.md) · [api-spec.md](./api-spec.md) · [logical-model.md](./logical-model.md)
> **방법론**: [Kent Beck TDD & Tidy First](../../guideline/tdd-guideline.md)
> **시작일**: 2026-07-13
> 배경·설계 결정(계층 배치, dual-write 방침, 키 계약)은 [.docs/week9/plan.md](../../../.docs/week9/plan.md) 참고. 테스트 진척은 본 문서가 단일 출처다.
> ⚠️ 랭킹은 두 앱에 걸친다 — **쓰기 = commerce-streamer**, **읽기 = commerce-api**. 각 항목 앞에 소속 앱을 표기한다.
> 주문 신호의 원천은 `ORDER_PAID`(결제 확정)다 — `ORDER_CREATED` 는 내부 이벤트로 랭킹에 오지 않는다(2026-07-14 확정, 판매량 집계와 동일 기준).
> 소비 구조(2026-07-14 확정): 랭킹은 **전용 consumer group** 이 같은 토픽을 독립 소비하고, 멱등 표식(`rank:seen`)과 증분을 **Redis Lua 로 원자 실행**한다 — 유실·중복 없음. 삭제 상품은 `PRODUCT_DELETED` 이벤트(신설)를 소비해 랭킹판에서 `ZREM` 으로 제거한다. 상세는 [logical-model.md](./logical-model.md) §4.

---

## 사용 규칙

- **한 줄 = 하나의 실패 테스트 = 하나의 Red → Green → Refactor 사이클**
- **단순한 것 → 복잡한 것** 순으로 배치 (Beck 의 "simplest test first")
- **행위/시나리오**를 적는다 — 구현 디테일이 아닌, 사용자/도메인이 관찰 가능한 행위로 서술
- 작업 중 떠오른 케이스는 즉시 plan.md 에 추가 (살아있는 문서)

### 진척 표시 규약
| 마크 | 의미 |
|---|---|
| `- [ ]` | 미시작 |
| `- [~]` | 진행 중 (Red 만 작성, Green 미완) |
| `- [x]` | 완료 (Green + 필요 시 Refactor 까지) |

### "go" 워크플로우
1. 사용자가 **"go"** → Claude 는 위에서부터 첫 번째 `- [ ]` 항목 1개를 찾는다
2. 실패 테스트(Red) 작성 → 최소 구현(Green) → 필요 시 구조 정리(Refactor)
3. 사용자 확인 후 체크박스 갱신 + 커밋 (구조/행위 분리)
4. 다음 "go" 까지 대기

---

### Phase 1 — 도메인 모델 (`com.loopers.domain.ranking`)
> 값 객체, 도메인 규칙. 스프링/Redis 의존 없이 순수 Kotlin 로 작성. 키·점수 규약은 [logical-model.md](./logical-model.md).

- [x] (streamer) 날짜로 랭킹판 키를 만들면 `rank:all:{yyyyMMdd}` 형식이 된다
- [x] (streamer) 시각이 주어지면 Asia/Seoul 기준 날짜의 키로 계산된다 — 자정 직후 시각은 새 날짜 키에 귀속된다
- [x] (streamer) 조회 신호 1건의 점수는 조회 가중치와 같다
- [x] (streamer) 좋아요 신호 1건의 점수는 좋아요 가중치와 같다
- [x] (streamer) 좋아요 취소 신호의 점수는 좋아요 가중치의 음수다
- [x] (streamer) 주문 신호의 점수는 수량 × 주문 가중치다
- [x] (streamer) 주문 1건(수량 1)의 점수가 좋아요 3건의 합보다 크다 — 가중치 관계(0.7 > 0.2×3)가 정책으로 고정된다
- [x] (commerce-api) `yyyyMMdd` 형식이 아닌 날짜 문자열로 랭킹판 키를 만들려 하면 예외가 발생한다
- [x] (commerce-api) 날짜 문자열 없이 키를 만들면 오늘(Asia/Seoul) 날짜의 키가 된다
- [x] (streamer) 시간별 집계는 발생 시각을 정시 버킷으로 귀속시킨다 — 10:37 신호는 10:00 버킷 (RDB SoT)
- [x] (streamer) 좋아요 취소 증분은 버킷 좋아요 수를 0 아래로도 내린다 — 버킷을 넘는 취소가 재계산을 왜곡하지 않는다

### Phase 2 — 도메인 서비스 (`com.loopers.domain.ranking.{Aggregate}Service`)
> 도메인 규칙 조합 + Repository 인터페이스를 통한 상태 변경.

_해당 없음 — 규칙은 순수 정책 계산기(Phase 1)에 있고, 정렬·순위·원자 증분은 저장소(Redis)가 가진다. 조합·조율은 Facade(Phase 4)가 담당한다._

### Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.ranking`)
> Redis 어댑터 구현. Testcontainers(Redis) 통합 테스트 — `modules/redis` testFixtures 재사용.

- [x] (streamer) 점수를 누적하면 랭킹판에서 그 상품의 점수가 증가한다
- [x] (streamer) 같은 상품에 여러 번 누적하면 점수가 합산된다
- [x] (streamer) **같은 eventId 로 두 번 누적을 시도하면 점수는 한 번만 반영된다** — 멱등 표식과 증분이 원자다(Lua)
- [x] (streamer) 음수 증분을 누적하면 점수가 감소하고, 0 아래로도 내려간다
- [x] (streamer) 점수 누적 시 랭킹판과 멱등 표식에 보존 기간(48시간) 만료가 설정된다
- [x] (streamer) 서로 다른 날짜 키에 누적한 점수는 섞이지 않는다
- [x] (streamer) 상품을 랭킹판에서 제거하면(오늘·어제 키) 이후 Top-N·순위 조회에 나오지 않는다
- [x] (commerce-api) Top-N 조회는 점수 내림차순으로 상품 식별자·점수를 반환한다
- [x] (commerce-api) 페이지 구간(시작 위치·크기)에 해당하는 항목만 반환한다
- [x] (commerce-api) 가장 점수 높은 상품의 순위는 1 이다 — 순위는 1-based 로 반환된다
- [x] (commerce-api) 랭킹판에 없는 상품의 순위 조회는 null 을 반환한다
- [x] (commerce-api) 랭킹판 전체 항목 수를 조회할 수 있다
- [x] (commerce-api) 존재하지 않는 날짜의 랭킹판 조회는 빈 목록을 반환한다
- [x] (streamer) 전일 랭킹판 점수에 이월 가중치를 곱해 다음 날 랭킹판으로 복사한다
- [x] (streamer) 이월 후 유입되는 점수는 이월 점수 위에 누적된다
- [x] (streamer) 전일 랭킹판이 없으면 이월은 아무것도 만들지 않는다
- [x] (streamer) 다음 날 랭킹판이 이미 존재하면 이월은 아무것도 하지 않는다 — 중복 실행·자정 후 오발동이 실점수를 덮어쓰지 않는다
- [x] (streamer) 시간별 집계를 저장하면 (상품, 버킷) 행에 누적되고, 같은 버킷 재저장은 합산된다 (RDB SoT)
- [x] (streamer) 서로 다른 시간 버킷의 수치는 섞이지 않는다
- [x] (streamer) 특정 날짜(KST)의 상품별 신호 합계를 조회한다
- [x] (streamer) 상품의 시간별 집계 행을 지운다 — 재실행해도 결과가 같다
- [x] (streamer) 랭킹판 재구축은 임시 키에 쌓아 원자 교체한다 — 기존 항목이 통째로 대체되고 TTL 이 설정된다
- [x] (streamer) 재구축할 점수가 없으면 기존 판을 건드리지 않는다

### Phase 4 — Application Facade (`com.loopers.application.ranking`)
> 유스케이스 진입점. 신호 반영 조율(쓰기) / 랭킹 조립(읽기). 트랜잭션 경계·예외 전파.

- [x] (streamer) 조회 신호가 들어오면 발생 시각 날짜의 랭킹판에 조회 가중치만큼 누적된다
- [x] (streamer) 좋아요 취소 신호는 해당 날짜 랭킹판 점수를 좋아요 가중치만큼 차감한다
- [x] (streamer) 주문 신호는 주문에 담긴 상품별로 수량 × 주문 가중치만큼 각각 누적한다 (멱등 단위 = eventId+productId)
- [x] (streamer) 같은 eventId 가 재전달되면 랭킹 점수가 다시 반영되지 않는다 — 자체 멱등 표식(Redis)이 걸러낸다 (어댑터 Idempotency 테스트 + Facade eventId 전달로 커버, 전 경로는 Phase 5 E2E)
- [x] (streamer) 상품 삭제 신호가 들어오면 오늘·어제 랭킹판에서 그 상품을 제거한다
- [x] (commerce-api) 상품을 삭제하면 삭제 사실이 외부 이벤트(`PRODUCT_DELETED`)로 발행된다 — outbox 적재
- [x] (commerce-api) 랭킹 조회는 점수 내림차순으로 상품 정보(이름·가격·브랜드·좋아요 수)가 조립된 목록을 반환한다
- [x] (commerce-api) 날짜 미지정 시 오늘(Asia/Seoul) 랭킹판을 조회한다
- [x] (commerce-api) 순위는 페이지를 넘어 이어진다 — 2페이지 첫 항목의 순위는 `page × size + 1` 이다
- [x] (commerce-api) 랭킹판에 있으나 삭제된 상품은 목록에서 제외된다
- [x] (commerce-api) 랭킹판이 없는 날짜는 빈 목록을 반환한다
- [x] (commerce-api) 전체 페이지 수는 총 개수를 페이지 크기로 올림해 계산된다 — 정수 연산(부동소수점 오차 배제)
- [x] (commerce-api) 상품 상세 조회 시 오늘 랭킹판 순위가 함께 반환된다
- [x] (commerce-api) 랭킹판에 없는 상품의 상세 순위는 null 이다
- [x] (commerce-api) 순위 조회가 실패해도(저장소 장애) 상품 상세는 정상 반환되고 순위만 null 이다
- [x] (commerce-api) 순위 폴백은 저장소 접근(인프라) 예외로 제한된다 — Redis 접근 실패만 null 로 폴백
- [x] (commerce-api) 순위 조회 중 인프라 외 런타임 오류는 전파된다 — 코드 결함을 성공 응답으로 가리지 않는다
- [x] (streamer) 이월 실행은 오늘 판을 소스로, 내일 판을 목적지로, 이월 가중치·보존 기간과 함께 저장소에 위임한다 — 콜드 스타트 완화
- [x] (streamer) 좋아요·조회·판매 반영 시 시간별 버킷이 같은 트랜잭션에서 함께 누적된다 — 발생 시각으로 귀속 (RDB SoT)
- [x] (streamer) 같은 eventId 재전달 시 시간별 집계도 다시 누적되지 않는다 — 기존 멱등 표식에 편승
- [x] (streamer) 상품 삭제 신호는 시간별 집계 행도 걷어낸다 — 재구축이 삭제 상품을 부활시키지 않는다 (누적 지표는 분석 이력으로 보존)
- [x] (streamer) 삭제 후 도착한 지연 신호는 시간별 집계를 되살리지 않는다 — 삭제 표식이 걸러낸다 (누적 지표는 계속 쌓인다)
- [x] (streamer) 삭제와 누적이 동시에 실행돼도 시간별 집계에 삭제 상품이 남지 않는다 — 같은 지표 행 잠금으로 직렬화
- [x] (streamer) 재구축은 날짜의 신호 합계에 가중치를 적용해 판을 다시 만든다
- [x] (streamer) 오늘 판 재구축에는 전일 판 이월분이 더해진다 — 전일 신호 재계산 × 이월 가중치(근사 복원)
- [x] (streamer) 자가 복구는 오늘 집계가 있는데 판이 없을 때만 재구축한다

### Phase 5 — Controller E2E (`com.loopers.interfaces.api.ranking`)
> HTTP 계약 검증. `ApiResponse` + `ApiControllerAdvice` 표준 응답. 계약은 [api-spec.md](./api-spec.md).

- [x] (commerce-api) `GET /api/v1/rankings` 가 200 과 표준 응답으로 점수 내림차순 랭킹 목록을 반환한다
- [x] (commerce-api) `date` 를 지정하면 그 날짜의 랭킹판이 조회된다
- [x] (commerce-api) `date` 형식이 `yyyyMMdd` 가 아니면 400 `RANKING_BAD_REQUEST` 를 반환한다
- [x] (commerce-api) 보존 기간 밖 날짜는 200 과 빈 목록을 반환한다
- [x] (commerce-api) `size` 가 상한을 넘으면 상한값으로 보정된다
- [x] (commerce-api) 상품 상세 응답에 `rank` 필드가 포함된다 — 랭킹판에 없는 상품은 null
- [x] (streamer) Kafka 로 발행된 행동 이벤트가 **랭킹 전용 consumer group** 에서 소비되어 랭킹판 점수에 반영된다 — 실 브로커·실 Redis (E2E)
- [x] (streamer) 같은 메시지를 재전달해도 랭킹판 점수는 한 번만 오른다 — 컨슈머 경유 멱등 E2E
- [x] (streamer) `PRODUCT_DELETED` 를 소비하면 랭킹판에서 그 상품이 사라진다 — 실 브로커·실 Redis
- [x] (streamer) 주문 1건(수량 1) 상품이 좋아요 3건 상품보다 랭킹판에서 상위다 — 가중치가 순서에 반영되는 E2E 검증
- [x] (streamer) 이월 스케줄은 매일 23:50 Asia/Seoul 로 예약된다 — cron 표현식이 다음 실행 시각을 그날 23:50 으로 계산한다
- [x] (streamer) 스케줄러 실행은 오늘(Asia/Seoul) 날짜 기준으로 이월을 위임한다
- [x] (streamer) 소비된 행동 이벤트가 시간별 집계 테이블에 쌓인다 — 실 브로커·실 DB E2E (RDB SoT)
- [x] (streamer) 판을 비운 뒤 재구축하면 집계 기반 점수로 판이 되살아난다 — 실 DB·실 Redis 통합
- [x] (streamer) 자가 복구 점검이 주기적으로 예약되고, 실행 시 오늘(Asia/Seoul) 기준 복구 점검을 위임한다

---

## 진행 로그

- 2026-07-13: /test-cases 로 초기 케이스 도출. ZSET 키 규약은 [logical-model.md](./logical-model.md) 에 확정.
- 2026-07-14: 주문 신호를 `ORDER_PAID`(결제 확정) 기준으로 확정. 선행 작업으로 commerce-api 에 `OrderEvent.Paid` 신설·`ORDER_CREATED` 내부화, streamer 판매 집계 매핑 교체 완료.
- 2026-07-14: 구현 전 상세 검토 — ① 랭킹은 전용 consumer group + Lua 원자 멱등(유실·중복 없음, 방침 격상) ② 이월 23:50 + 목적지 존재 시 스킵 ③ 삭제 상품은 `PRODUCT_DELETED` 신설 + ZREM ④ 시간 창 KST 달력일·occurredAt 귀속 확정. 케이스 갱신 반영.
- 2026-07-14: Phase 1 완료 — streamer `RankingKey`(일자·발생시각)·`RankingScorePolicy`(신호별 가중치·취소 음수·주문 수량)·`RankingSignal`·`RankingWeights`, commerce-api `RankingKey`(문자열 파싱·오늘 기본)·`RankingErrorType`.
