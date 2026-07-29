-- Phase 2 after — ProductEntity @Table(indexes=...) 와 동일한 6개 인덱스를 30만 데이터에 적용 후 실측.
-- (앱 재기동=ddl-auto:create 는 데이터를 날리므로, 시드 보존 위해 동일 DDL 을 직접 CREATE INDEX)
-- 정렬은 toJpaSort 통일 후 기준: LATEST=created_at desc/id desc, PRICE_ASC=price asc/id asc, LIKES_DESC=like_count desc/id desc
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < after_index.sql

CREATE INDEX idx_products_created_id ON products (created_at, id);
CREATE INDEX idx_products_price_id ON products (price, id);
CREATE INDEX idx_products_like_id ON products (like_count, id);
CREATE INDEX idx_products_brand_created_id ON products (brand_id, created_at, id);
CREATE INDEX idx_products_brand_price_id ON products (brand_id, price, id);
CREATE INDEX idx_products_brand_like_id ON products (brand_id, like_count, id);
ANALYZE TABLE products;

SELECT '--- (1) LATEST page0 ---' AS q;
EXPLAIN ANALYZE SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '--- (2) LATEST deep offset 100000 ---' AS q;
EXPLAIN ANALYZE SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 100000;

SELECT '--- (3) LIKES_DESC page0 ---' AS q;
EXPLAIN ANALYZE SELECT * FROM products WHERE deleted_at IS NULL
ORDER BY like_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '--- (4) brand=7 + PRICE_ASC (id asc tiebreak) ---' AS q;
EXPLAIN ANALYZE SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 7
ORDER BY price ASC, id ASC LIMIT 20 OFFSET 0;

SELECT '--- (5) brand=7 + LIKES_DESC ---' AS q;
EXPLAIN ANALYZE SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 7
ORDER BY like_count DESC, id DESC LIMIT 20 OFFSET 0;

SELECT '--- (6b) COUNT brand=7 ---' AS q;
EXPLAIN ANALYZE SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND brand_id = 7;
