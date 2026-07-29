-- Round5 읽기 성능 실습용 대용량 시드
-- products 30만 건 + brands 100 건. 각 컬럼 값은 다양하게 분포시킨다
--   - brand_id : 1~100 균등        → 브랜드 필터 선택도 ≈ 3,000건/브랜드
--   - price    : 1,000~1,000,000   → 가격 정렬용
--   - like_count : POW(RAND(),3) 으로 좌편향(대부분 낮고 소수만 높음) → 인기순 정렬용 현실 분포
--   - created_at : 최근 2년 랜덤    → 최신순 정렬용
--   - sales_status : ON_SALE ≈ 80%
--   - deleted_at : ≈ 2% soft-delete → @SQLRestriction(deleted_at IS NULL) 술어 재현
-- 직접 MySQL 에서 실행. 앱(local=ddl-auto:create) 재기동 시 사라지므로 측정 세션 동안만 유지한다.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < seed.sql

SET SESSION cte_max_recursion_depth = 2000000;

DELETE FROM products;
DELETE FROM brands;

-- 브랜드 100개
INSERT INTO brands (name, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT CONCAT('브랜드-', n), NOW(6), NOW(6), NULL
FROM seq;

-- 상품 30만개
INSERT INTO products (name, price, stock, like_count, brand_id, sales_status, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 300000
)
SELECT
    CONCAT('상품-', n),
    1000 + FLOOR(RAND() * 999000),
    FLOOR(RAND() * 500),
    FLOOR(POW(RAND(), 3) * 100000),
    1 + FLOOR(RAND() * 100),
    ELT(1 + FLOOR(RAND() * 10),
        'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE', 'ON_SALE',
        'ON_SALE', 'ON_SALE', 'ON_SALE', 'OUT_OF_STOCK', 'OFF_SALE'),
    NOW(6) - INTERVAL FLOOR(RAND() * 730) DAY - INTERVAL FLOOR(RAND() * 86400) SECOND,
    NOW(6),
    IF(RAND() < 0.02, NOW(6) - INTERVAL FLOOR(RAND() * 365) DAY, NULL)
FROM seq;

ANALYZE TABLE products;
ANALYZE TABLE brands;

SELECT
    (SELECT COUNT(*) FROM products) AS total_products,
    (SELECT COUNT(*) FROM products WHERE deleted_at IS NULL) AS alive_products,
    (SELECT COUNT(DISTINCT brand_id) FROM products) AS distinct_brands,
    (SELECT MAX(like_count) FROM products) AS max_like;
