-- 정규화 역실험용 likes 시드 (약 200만 건, 인기 집중 분포)
-- 목적: "좋아요 순 정렬"을 정규화(join+group by)로 풀 때의 비용을 비정규화(like_count 컬럼)와 비교하기 위함.
--   - user_id   : 1~30만 (유저 분산 → (user,product) 유니크 충돌 최소)
--   - product_id: POW(RAND(),2.5) 좌편향 → 낮은 id 상품에 좋아요 집중(인기 상품 재현)
-- 주의: products.like_count(독립 시드)와 값이 일치하지 않는다. 본 실험은 "값 일치"가 아니라 "쿼리 구조 비용"을 비교한다.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < seed_likes.sql

SET SESSION cte_max_recursion_depth = 3000000;

DELETE FROM likes;

INSERT IGNORE INTO likes (user_id, product_id, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2000000
)
SELECT
    1 + FLOOR(RAND() * 300000),
    1 + FLOOR(POW(RAND(), 2.5) * 299999),
    NOW(6),
    NOW(6),
    NULL
FROM seq;

ANALYZE TABLE likes;

SELECT
    (SELECT COUNT(*) FROM likes) AS total_likes,
    (SELECT COUNT(DISTINCT product_id) FROM likes) AS products_with_likes,
    (SELECT MAX(c) FROM (SELECT COUNT(*) c FROM likes GROUP BY product_id) t) AS max_likes_of_one_product;
