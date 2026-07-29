-- Phase 1 baseline — 인덱스 없는 상태의 실행계획/실측
-- 앱의 실제 쿼리(@SQLRestriction deleted_at IS NULL + Spring Data 정렬/페이징)를 그대로 본뜬다.
-- ProductRepositoryImpl.toJpaSort(): LATEST=created_at desc, PRICE_ASC=price asc, LIKES_DESC=like_count desc (+ id desc tiebreak)
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < baseline.sql

-- ① 최신순 1페이지 (필터 없음)
SELECT '--- (1) LATEST page0 ---' AS q;
EXPLAIN ANALYZE
SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0;

-- ② 최신순 깊은 페이지 (offset 100000) — deep offset 비용
SELECT '--- (2) LATEST deep offset 100000 ---' AS q;
EXPLAIN ANALYZE
SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 100000;

-- ③ 인기순 1페이지 (필터 없음)
SELECT '--- (3) LIKES_DESC page0 ---' AS q;
EXPLAIN ANALYZE
SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY like_count DESC, id DESC LIMIT 20 OFFSET 0;

-- ④ 브랜드 필터 + 가격 오름차순
SELECT '--- (4) brand_id=7 + PRICE_ASC ---' AS q;
EXPLAIN ANALYZE
SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 7
ORDER BY price ASC, id DESC LIMIT 20 OFFSET 0;

-- ⑤ 브랜드 필터 + 인기순 (과제 핵심 시나리오)
SELECT '--- (5) brand_id=7 + LIKES_DESC ---' AS q;
EXPLAIN ANALYZE
SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 7
ORDER BY like_count DESC, id DESC LIMIT 20 OFFSET 0;

-- ⑥ count 쿼리 (Spring Data Page 가 매 요청 동반)
SELECT '--- (6a) COUNT no filter ---' AS q;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL;

SELECT '--- (6b) COUNT brand_id=7 ---' AS q;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND brand_id = 7;
