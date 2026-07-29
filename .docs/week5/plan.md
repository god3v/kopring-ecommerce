# Round 5 — 읽기 성능 최적화 학습/구현 플랜

> 메인 타깃: **상품 목록 조회 + 상세 조회** (`GET /api/v1/products`, `GET /api/v1/products/{id}`)
> 진행 원칙: **측정 먼저** — "측정하지 않은 최적화는 추측이다". 각 Phase 는 학습(개념) → 구현 → before/after 측정 순.

## 진행 순서 (측정 우선)

- [x] **Phase 0. 측정 환경 구축** — 대용량 시드(30만 상품/100 브랜드) + EXPLAIN/측정 스크립트
- [x] **Phase 1. 조회 병목 분석** — 정렬·필터·페이징별 baseline EXPLAIN, filesort/풀스캔/offset 비용 식별 → `benchmark.md`
- [x] **Phase 2. 인덱스 설계** — 6개 복합 인덱스 + 타이브레이크 통일, before/after (1000x↑) → `benchmark.md`
- [ ] **Phase 3. 정렬/필터 최적화** — 커서(keyset) 페이징, count 비용, 커버링 인덱스
- [x] **Phase 4. 좋아요 수 구조 개선** — 비정규화 검증 + 좋아요 등록/취소 ↔ like_count 동기화 (기구현·검증 완료)
- [x] **Phase 5. Redis 캐시** — 상세/목록 read-through(port+adapter), TTL·키·무효화, 장애 흡수(DB 폴백). L1(로컬캐시)은 측정 후 도입으로 보류

## 공식 체크리스트 (week5_request.md) 매핑

### 🔖 Index
- [x] 상품 목록 API 에서 brandId 기반 검색, 좋아요 순 정렬 처리 → Phase 1,2
- [x] 필터/정렬 유즈케이스 분석 → 인덱스 적용 + 전후 성능비교 → Phase 1,2 (완료), 심화 Phase 3(커서)

### ❤️ Structure
- [x] 목록/상세에서 좋아요 수 조회 및 좋아요 순 정렬 가능하도록 구조 개선 → Phase 4 (like_count 비정규화 + 응답 노출 + LIKES_DESC 인덱스)
- [x] 좋아요 적용/해제 시 like_count 동기화 → Phase 4 (원자 증감 + 멱등 + 동시성 4종 테스트 green)

### ⚡ Cache
- [x] Redis 캐시 적용 + TTL 또는 무효화 전략 → Phase 5 (상세 TTL 5분 + 수정/삭제 evict, 목록 TTL 60초)
- [x] 캐시 미스 상황에서도 정상 동작 → Phase 5 (어댑터가 Redis 예외 흡수 → get=null/put=no-op → DB 폴백, 단위 테스트로 검증)

## 산출물
- `sql/seed.sql` — 대용량 시드
- `sql/baseline.sql` — baseline EXPLAIN 쿼리 모음
- `sql/after_index.sql` — 인덱스 적용 + 재측정 쿼리
- `benchmark.md` — Phase 별 before/after 측정 기록(raw 로그)
- `index-optimization-report.md` — 필터 유형·분석·인덱스 근거·전후 성능비교 리포트
- `cache-design-report.md` — 캐시 대상·구조 결정(@Cacheable/데코레이터 비교)·키/TTL/무효화·장애 안전성
