# product 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 UC-1·UC-2(회원 카탈로그) + UC-3~7(관리자 상품) 을 포괄한다.
> 관리자 인증은 **interfaces 레이어 책임** 으로 가정 — Facade 는 "인증 통과한 호출" 로만 다룬다.
> **브랜드(brand) 는 별도 애그리거트 — [brand/plan.md](../brand/plan.md) 로 분리**. Product 는 `brandId` 로 ID 참조한다.

---

## 0. 스코프

- requirements.md 의 UC-1·UC-2 + UC-3~7 (상품 회원 카탈로그 + 관리자 상품)
- `likedByMe` 는 like 도메인 도입 전이므로 **비회원/미인증 시 false 반환만 보장**했고, 이후 like 도메인에서 실제 lookup 으로 연동됨.

### 결정 사항
- `likeCount` 는 `Product` 비정규화 필드 (외부 like 도메인이 갱신).
- Repository 는 기본으로 **soft-deleted 자원을 결과에서 제외** (`@SQLRestriction("deleted_at IS NULL")`). 회원·관리자 모두 동일 (requirements §5).
- SalesStatus 값 집합: `ON_SALE` · `OUT_OF_STOCK` · `OFF_SALE` (전이 규칙 단순 — 자유 전이 허용). 초깃값 `ON_SALE`.
- Facade 는 애그리거트별 단일 — `ProductFacade` (product UC 전체). 회원·관리자 결과 DTO 는 별도.

### interfaces 결정 (Phase 5~9)
- **컨트롤러 분리**: 회원 `ProductV1Controller`(`/api/v1/products`) / 관리자 `ProductV1AdminController`(`/api-admin/v1/products`). 관리자 인증은 경로 기준 `AdminAuthInterceptor`(brand Phase 5 공유 인프라) 가 담당 — product 는 신규 인증 작업 없음.
- **목록 페이징**: 회원·관리자 모두 `PageResult<T>`(like/brand 와 일관). 컨트롤러는 `Pageable`(`@PageableDefault(size=20)`) 로 수신해 `PageQuery` 로 정규화. 정렬은 회원만 `sort` 쿼리(`ProductSortType.from`, 미지원 값 `400`), 관리자는 최신순 고정.
- **회원 목록 입력 합성**: `GetProductsQuery(sort, brandId, paging: PageQuery)` (like `GetMyLikesQuery` 와 동형). 관리자 목록은 필터 1개·정렬 없음이라 `(brandId, PageQuery)` 직접 전달.
- **선택 인증**: 회원 상세의 `likedByMe` 를 위해 **신규 어노테이션 없이** `AuthInterceptor` 를 확장한다 — "헤더가 있으면 `@RequireAuth` 유무와 무관하게 인증 시도", `@RequireAuth` 는 *헤더 필수* 의미만. `@LoginUser` 파라미터를 **nullable `AuthUser?`** 로 해석. 비필수 경로에서 헤더 있으나 인증 실패해도 **거부하지 않고** AuthUser 미주입(`likedByMe=false`). (Phase 7.0, 구조 커밋 선행)
- **salesStatus 직렬화**: 와이어는 `SalesStatus.key`(snake_case). 도메인 enum 비오염을 위해 **interfaces DTO 단 매핑**(응답 `key`, 요청 `SalesStatus.from`). (Phase 8.0)
- **HTTP**: 등록·수정 `200 OK` + 리소스, 삭제 `200 OK` + `data: null` (brand 와 동일 컨벤션).

---

## 1. Phase 1 — Domain (`com.loopers.domain.product`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 Product 생성 불변식
- [x] Product 를 음수 price 로 생성하면 예외가 발생한다
- [x] Product 를 음수 stock 으로 생성하면 예외가 발생한다
- [x] Product 를 음수 likeCount 로 생성하면 예외가 발생한다
- [x] Product 를 blank name 으로 생성하면 예외가 발생한다

### 1.2 Product 재고 차감 (`deductStock`)
- [x] 양수 quantity 로 호출하면 stock 이 그만큼 줄어든다
- [x] quantity 가 stock 과 같으면 호출 결과로 stock 이 0 이 된다
- [x] quantity 가 stock 보다 크면 `INSUFFICIENT_STOCK` 예외가 발생한다 (음수 방지)
- [x] quantity 가 0 이거나 음수면 예외가 발생한다

### 1.3 ProductSortType
- [x] `from("latest")` · `from("price_asc")` · `from("likes_desc")` 가 대응 enum 으로 매핑된다
- [x] `from(null)` 은 `LATEST` 로 해석된다 (기본값)
- [x] `from(미지원 문자열)` 은 `PRODUCT_BAD_REQUEST` 예외가 발생한다

### 1.4 SalesStatus (enum)
- [x] enum 이 `ON_SALE` · `OUT_OF_STOCK` · `OFF_SALE` 세 값을 보유한다
- [x] `from("on_sale" 등)` 가 대응 enum 으로 매핑된다 (소문자 key)
- [x] `from(미지원 문자열)` 은 `PRODUCT_BAD_REQUEST` 예외가 발생한다

### 1.5 Product soft delete
- [x] `softDelete()` 호출 시 `deletedAt` 이 설정된다
- [x] `softDelete()` 된 Product 는 `isDeleted()` 가 true 다
- [x] 이미 삭제된 Product 의 `softDelete()` 재호출은 멱등이다 (deletedAt 변동 없음)

### 1.6 Product 등록 시 SalesStatus 초깃값
- [x] `Product.create(...)` 시 salesStatus 가 `ON_SALE` 로 기본 설정된다

### 1.7 Product 업데이트 (`update`)
- [x] `update(name, price, salesStatus)` 로 세 필드가 갱신된다
- [x] `update` 인자에 brandId 는 받지 않는다 (등록 이후 불변)

---

## 2. Phase 2 — Application port (`ProductRepository`)

인터페이스만 정의. 테스트 없음 (port impl 은 Phase 4 가 검증).

- `save(product): Product`
- `findById(id): Product?` — soft-deleted 제외
- `findAll(sort, brandId?, page, size): List<Product>` — soft-deleted 제외
- `findAllForAdmin(brandId?, page, size): List<Product>` — soft-deleted 제외, 최신순 고정
- `existsByBrandIdAndName(brandId, name): Boolean` — soft-deleted 제외 (유일성 검증)
- `findAllByBrandId(brandId): List<Product>` — brand UC-6 카스케이드용

---

## 3. Phase 3 — Application Facade (`application.product.ProductFacade`)

mock 기반 단위 테스트.

#### UC-1. `getProducts(sort, brandId?, page, size)` — 회원 카탈로그
- [x] sort=`LATEST` 로 조회하면 Repository 에 동일 sort 가 전달된다 (정렬 결과 검증은 Phase 4)
- [x] sort=`PRICE_ASC` 로 조회하면 Repository 에 동일 sort 가 전달된다
- [x] sort=`LIKES_DESC` 로 조회하면 Repository 에 동일 sort 가 전달된다
- [x] sort 가 null 이면 `LATEST` 로 해석된다
- [x] brandId 필터를 지정하면 Repository 에 동일 필터가 전달된다
- [x] page / size 가 Repository 에 전달된다

#### UC-2. `getProductDetail(productId, loginId?)` — 회원 상세
- [x] 존재하는 productId 로 호출하면 `ProductDetailResult` (product · brand · likeCount · likedByMe=false) 가 반환된다
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다
- [x] product 는 존재하나 brand 가 없으면 `BRAND_NOT_FOUND` 예외가 발생한다 (정합성)

#### UC-3. `getProductsForAdmin(brandId?, page, size)` — 관리자 목록
- [x] `findAllForAdmin` 으로 위임된다 (정렬·페이징 검증은 Phase 4)
- [x] brandId 필터가 Repository 에 전달된다
- [x] 응답 항목에 salesStatus 가 포함된다

#### UC-4. `getProductForAdmin(productId)` — 관리자 상세
- [x] 존재하는 productId 로 호출하면 `AdminProductDetailResult` (product · brand · salesStatus) 가 반환된다
- [x] 존재하지 않거나 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

#### UC-5. `registerProduct(command)` — 관리자 등록
- [x] 유효한 입력으로 등록하면 salesStatus=`ON_SALE` 의 신규 Product 가 저장된다
- [x] 지정 brandId 가 존재하지 않으면 `BRAND_NOT_FOUND` 예외가 발생한다
- [x] 같은 브랜드 안에 같은 이름이 이미 있으면 `DUPLICATE_PRODUCT_NAME` 예외가 발생한다

#### UC-6. `updateProduct(productId, command)` — 관리자 수정
- [x] 정상 입력으로 수정하면 name / price / salesStatus 가 갱신된다
- [x] 존재하지 않거나 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다
- [x] 같은 브랜드에 같은 이름의 다른 상품이 있으면 `DUPLICATE_PRODUCT_NAME` 예외가 발생한다 (자기 자신 제외)
- [x] 이름이 동일하면 중복 검사를 건너뛴다 (자기 자신 제외 보장)

#### UC-7. `deleteProduct(productId)` — 관리자 삭제
- [x] 정상 호출 시 Product 가 soft delete 된다 (저장됨)
- [x] 존재하지 않거나 이미 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

---

## 4. Phase 4 — Infrastructure adapter (`ProductRepositoryImpl`)

Testcontainers 통합 테스트 (`modules:jpa` testFixtures 재사용).

- [x] save 후 findById 로 동일 도메인 객체가 복원된다
- [x] findById 는 soft-deleted Product 를 null 로 반환한다
- [x] findAll(sort=LATEST) 가 createdAt desc 로 정렬된다
- [x] findAll(sort=PRICE_ASC) 가 price asc 로 정렬된다
- [x] findAll(sort=LIKES_DESC) 가 likeCount desc 로 정렬된다
- [x] findAll 은 soft-deleted Product 를 제외한다
- [x] findAll 에 brandId 필터가 적용된다
- [x] findAllForAdmin 이 createdAt desc + soft delete 제외 + brandId 필터를 적용한다
- [x] existsByBrandIdAndName 가 soft-deleted 를 제외하고 판정한다
- [x] findAllByBrandId 가 해당 브랜드의 (soft-deleted 제외) 상품을 모두 반환한다
- [x] page / size 페이징이 적용된다

---

## 5. Phase 5 — 관리자 채널 인증 (공유 인프라) — ✅ brand 에서 완료, 재사용

> `/api-admin/**` 전 경로 인증은 [brand/plan.md](../brand/plan.md) Phase 5 에서 이미 구축됐다. product 는 **그대로 재사용** 하며 신규 작업이 없다.

- [x] `AdminAuthInterceptor` (`X-Loopers-Ldap` 헤더 검증, 기대값 설정 주입) — brand 에서 완료
- [x] `WebMvcConfig` 에 `addPathPatterns("/api-admin/**")` 등록 — `/api-admin/v1/products` 도 자동 포함

> 따라서 product 관리자 엔드포인트는 인터셉터를 추가 등록할 필요 없이 경로만 `/api-admin/v1/products` 로 두면 인증이 적용된다. E2E 에서 `X-Loopers-Ldap` 헤더 누락 → `401` 만 회귀 검증한다(값 불일치는 `AdminAuthInterceptorTest` 가 이미 커버).

---

## 6. Phase 6 — 목록 PageResult 전환 + Query DTO 합성 (구조 우선 — Tidy First)

> brand Phase 6 과 동형이되 **목록 메서드가 2개**(회원 `findAll` + 관리자 `findAllForAdmin`)다. 포트 반환형 변경이 Facade 까지 컴파일 연쇄되므로 한 원자적 구조 단위다. 외부 엔드포인트 도입 전이라 동작 불변(기존 테스트 갱신). 행위 변경 없음 — 별도 커밋.

### 6.1 어댑터·포트 반환형 `List` → `PageResult` (`@Query` 미사용)
- [x] **(구조)** `@Query` 의 `(:brandId IS NULL OR …)` 동적 필터를 제거하고 **파생 쿼리 + 어댑터 분기**로 구현 — `ProductJpaRepository.findAllByBrandId(brandId, pageable): Page<ProductEntity>`(필터 O) + 전체는 `JpaRepository.findAll(Pageable)`, `ProductRepositoryImpl.findPage(brandId, pageRequest)` 가 null 여부로 분기. `Page` → `PageResult<Product>` 변환 헬퍼(`toPageResult`). 정렬에 **tie-breaker `id desc` 고정**(brand 와 동일 — `createdAt`/`price`/`likeCount` 동률 시 페이지 간 중복·누락 방지). 포트 `ProductRepository.findAll`/`findAllForAdmin` 반환 `List<Product>` → `PageResult<Product>`. `ProductRepositoryImplIntegrationTest` 의 정렬·필터·페이징 케이스를 `.content` 기준으로 갱신 + `totalElements`/`totalPages` 검증 추가(`@SQLRestriction` 이 count 에도 적용됨을 `findAll`·파생 쿼리 양쪽에서 확인).

### 6.2 Facade 반환형 전파 + 회원 목록 Query DTO 합성
- [x] **(구조)** `ProductFacade.getProducts(sort, brandId, page, size)` → `getProducts(query: GetProductsQuery): PageResult<ProductSummaryResult>` — `like` 의 `GetMyLikesQuery` 와 동형으로 `application.product.query.GetProductsQuery(sort: ProductSortType?, brandId: Long?, paging: PageQuery)` 신설(합성). `getProductsForAdmin(brandId, page, size)` → `(brandId: Long?, pageQuery: PageQuery): PageResult<AdminProductSummaryResult>`(brand `getBrandsForAdmin` 과 동일 형태 — 관리자 목록은 정렬 입력이 없어 필터 1개라 공용 `PageQuery` 직접 사용). 둘 다 `PageResult.map` 으로 결과 DTO 매핑. `ProductFacadeTest` 의 UC-1·UC-3 케이스를 `.content` 기준으로 갱신 + 페이지 메타 전파 검증.

---

## 7. Phase 7 — 회원 상품 API (행위 — Red→Green, E2E)

> `GET /api/v1/products`(목록), `GET /api/v1/products/{productId}`(상세). 산출물: `ProductV1Controller`(`/api/v1/products`), `ProductV1ApiSpec`, `ProductV1Dto`(`ProductResponse`{id,name,price,likeCount,brandId}, `ProductsResponse`=PageResult 형태, `ProductDetailResponse`{…,brandName,likedByMe}). 회원 채널이며 **목록은 인증 불필요**, **상세는 선택 인증**.

### 7.0 (구조 선행) 선택 인증 경로 도입 — `likedByMe`
> 현재 `AuthInterceptor` 는 `@RequireAuth` 가 붙은 핸들러에서만 동작하고 헤더 누락 시 `401` 로 **거부**하며, `LoginUserArgumentResolver` 는 `AuthUser` 부재 시 예외를 던진다. 그러나 상세(UC-2)는 *선택 인증* 이다 — 헤더가 오면 회원을 식별해 `likedByMe` 를 채우되, 헤더가 없거나 **인증에 실패해도 거부하지 않고** `likedByMe=false` 로 응답해야 한다(api-spec §0.1).
>
> **채택안**: 신규 어노테이션 없이 `AuthInterceptor` 를 *"헤더가 있으면 `@RequireAuth` 유무와 무관하게 인증 시도"* 로 확장하고, `@RequireAuth` 는 *헤더 필수* 의미만 가진다. `@LoginUser` 파라미터는 **nullable `AuthUser?`** 로 해석한다.
>
> | 헤더 | `@RequireAuth` | 인증 | 동작 |
> |---|---|---|---|
> | 없음 | 없음 | — | 통과 (AuthUser 미주입) |
> | 있음 | 없음 | 성공 | 인증 후 통과 (AuthUser 주입) |
> | 있음 | 없음 | 실패 | **거부 없이 통과** (AuthUser 미주입) |
> | 없음 | 있음 | — | `401` |
> | 있음 | 있음 | 성공 | 인증 후 통과 (AuthUser 주입) |
> | 있음 | 있음 | 실패 | `401` |
>
> - `AuthInterceptor.preHandle`: 헤더(둘 다 non-blank) 없으면 → `@RequireAuth` 면 `401`, 아니면 통과. 헤더 있으면 → `authenticate` 시도, 성공 시 `AuthUser` attribute 저장, **실패 시 `@RequireAuth` 면 `401` / 아니면 삼키고 통과**(attribute 미설정).
> - `LoginUserArgumentResolver`: `parameter.isOptional`(Spring 이 Kotlin `AuthUser?` nullability 인식) 이면 attribute 부재 시 `null` 반환, 아니면 기존대로 `401`.
> - 컨트롤러: `getProduct(@LoginUser user: AuthUser?, …)` → `productFacade.getProductDetail(productId, user?.id)`.
>
> 이 한 단계는 인증 인프라의 **구조 변경**이라 행위 테스트(7.2)와 **별도 커밋**으로 선행한다. 단위 테스트(MockMvc standalone, 스텁 컨트롤러): ① 헤더 동반·정상 → `AuthUser` 주입 ② 헤더 누락(@RequireAuth 없음) → `null` 주입(거부 안 함) ③ 헤더 동반·인증 실패(@RequireAuth 없음) → `null` 주입(거부 안 함) ④ 헤더 누락(@RequireAuth 있음) → `401` 회귀 ⑤ 헤더 동반·인증 실패(@RequireAuth 있음) → `401` 회귀.

- [x] **(구조)** 헤더 존재 시 항상 인증 + nullable `@LoginUser` 해석 도입 (위 표). 기존 `@RequireAuth`(헤더 필수·실패 시 401) 동작은 보존.

### 7.1 UC-1 목록 — `GET /api/v1/products`
- [x] 인증 없이 조회하면 `200 OK` + PageResult(content + page/size/totalElements/totalPages), 기본 정렬 **latest**
- [x] `brandId` 필터를 지정하면 해당 브랜드 상품만 응답된다
- [x] `sort=price_asc` 를 지정하면 가격 오름차순으로 응답된다(컨트롤러가 `ProductSortType.from(sort)` 로 매핑)
- [x] `sort` 가 허용 집합(`latest`/`price_asc`/`likes_desc`) 밖 → `400 PRODUCT_BAD_REQUEST`
- [x] `page`/`size` 가 반영되고 `totalElements`/`totalPages` 가 정확(삭제 마크 상품 제외)

### 7.2 UC-2 상세 — `GET /api/v1/products/{productId}`
- [x] 존재하는 상품 → `200 OK` + `{ id, name, price, likeCount, brandId, brandName, likedByMe }`
- [x] 미인증(헤더 없음) 요청은 `likedByMe=false`
- [x] 인증 회원이 좋아요한 상품은 `likedByMe=true`
- [x] 인증 헤더가 와도 **인증 실패 시 거부 없이** `likedByMe=false` (선택 인증)
- [x] 미존재/삭제 마크 → `404 PRODUCT_NOT_FOUND`

---

## 8. Phase 8 — 관리자 상품 API (행위 — Red→Green, E2E)

> `/api-admin/v1/products`. 산출물: `ProductV1AdminController`, `ProductV1AdminApiSpec`, `ProductV1Dto`(`AdminProductResponse`{…,salesStatus}, `AdminProductsResponse`=PageResult 형태, `AdminProductDetailResponse`, `RegisterProductRequest`, `UpdateProductRequest`). 인증 헤더 `X-Loopers-Ldap`(Phase 5 인터셉터가 경로로 강제).

### 8.0 (구조 선행) `salesStatus` key 직렬화
> 와이어 값은 `SalesStatus.key`(snake_case: `on_sale`/`out_of_stock`/`off_sale`)다 — 기본 enum 직렬화(`ON_SALE`)와 다르다(api-spec §0.3).
> **채택안(권장)**: 도메인 enum 을 오염시키지 않도록 **interfaces DTO 단에서 매핑** — 응답은 `salesStatus = result.salesStatus.key`(String), 수정 요청은 `SalesStatus.from(request.salesStatus)`(미지원 값 → `400 PRODUCT_BAD_REQUEST`, `SalesStatus.from` 이 이미 던짐). (대안: enum 에 `@JsonValue`/`@JsonCreator` — 채택 시 별도 결정.)

- [x] **(구조)** 응답 DTO 의 `salesStatus` String(key) 매핑 + 요청 DTO 의 `SalesStatus.from` 역매핑 확정. DTO 단위 테스트로 key↔enum 왕복 검증.

### 8.1 UC-3 목록 — `GET /api-admin/v1/products`
- [x] 인증 관리자가 조회하면 `200 OK` + PageResult, **최신순**, 각 항목에 `salesStatus`(key) 포함
- [x] `brandId` 필터가 반영된다
- [x] 등록된 상품이 없으면 빈 content + 페이지 메타
- [x] `page`/`size` 가 반영되고 `totalElements`/`totalPages` 가 정확

### 8.2 UC-4 상세 — `GET /api-admin/v1/products/{productId}`
- [x] 존재하는 상품 → `200 OK` + `{ …, brandName, salesStatus }`
- [x] 미존재/삭제 마크 → `404 PRODUCT_NOT_FOUND`

### 8.3 UC-5 등록 — `POST /api-admin/v1/products`
- [x] 정상 등록 → `200 OK` + 생성 상품(`salesStatus=on_sale`, `likeCount=0`)
- [x] 입력 형식 위반(이름/가격/재고) → `400 PRODUCT_BAD_REQUEST`
- [x] 지정 브랜드 미존재/삭제 마크 → `404 BRAND_NOT_FOUND`
- [x] 같은 브랜드 내 이름 중복 → `409 DUPLICATE_PRODUCT_NAME`

### 8.4 UC-6 수정 — `PUT /api-admin/v1/products/{productId}`
- [x] 정상 수정 → `200 OK` + 갱신(name/price/salesStatus). `brandId` 는 입력에 받지 않음
- [x] 미존재/삭제 마크 → `404 PRODUCT_NOT_FOUND`
- [x] 같은 브랜드에 같은 이름의 다른 상품 존재(자기 제외) → `409 DUPLICATE_PRODUCT_NAME`
- [x] 입력 형식 위반 또는 `salesStatus` 미지원 값 → `400 PRODUCT_BAD_REQUEST`

### 8.5 UC-7 삭제 — `DELETE /api-admin/v1/products/{productId}`
- [x] 정상 삭제 → `200 OK` + `data: null`, soft delete (E2E: 삭제 후 회원 상세 `GET /api/v1/products/{id}` 가 `404` = 삭제 확인)
- [x] 미존재/이미 삭제 마크 → `404 PRODUCT_NOT_FOUND`

### 8.6 관리자 인증 (전 엔드포인트 공통)
- [x] `X-Loopers-Ldap` 헤더 누락 → `401 UNAUTHORIZED` (목록 엔드포인트 E2E 로 회귀 검증)

---

## 9. Phase 9 — api-spec / 문서 동기화 (문서)

- [x] api-spec 부록 "구현 메모" 의 목록 페이징 단서(현재 `List` 반환 → 전환 선행)를 **전환 완료** 사실로 갱신 — 회원/관리자 목록 PageResult 계약이 실제 구현과 일치함을 확정.
- [x] `salesStatus` 직렬화 채택안(DTO 단 key 매핑) 을 부록에 기록.
- [x] 선택 인증(헤더 존재 시 항상 인증 + nullable `@LoginUser`) 도입 사실 기록(product api-spec 부록 또는 admin/logical-model 참조).

---

## 진행 로그

- 2026-05-28: plan 초안 작성, Phase 1 (4 라인 스코프) 11 개 완료.
- 2026-05-29: UC 전체 포함으로 스코프 확장 — plan 재작성.
- 2026-05-29: Phase 2 port 인터페이스 정의 완료 (ProductRepository · BrandRepository).
- 2026-05-29: Phase 3 진입. UC-1 #1 (sort 전달) Green — ProductFacade · ProductSummaryResult · ProductFixture 도입.
- 2026-05-29: Phase 3 전체 (UC-1 ~ UC-13, 34 tests) 완료. ProductFacade · BrandFacade.
- 2026-05-29: Phase 4 infra 어댑터 도입 — Product · Brand 엔티티/JpaRepository/RepositoryImpl. SQLRestriction(deleted_at IS NULL) 으로 soft delete 제외.
- 2026-05-29: Phase 4 통합 테스트 전체 (15 tests) Green. Testcontainers MySQL.
- 2026-06-09: **brand 도메인 분리** — brand 의 Phase 1~4 체크리스트를 [brand/plan.md](../brand/plan.md) 로 이동. 본 plan 은 상품(UC-1·2·9~13) 전용. 상품 interfaces 계층은 brand 이후 별도 수립.
- 2026-06-09: 상품 UC 연속 재정립 — 관리자 UC-9~13 → UC-3~7 (requirements v0.5). requirements·api-spec·plan UC 참조 동기화.
- 2026-06-09: **Phase 7~9 완료** — interfaces 계층 전부. Phase 7.0(구조): `AuthInterceptor` 를 "헤더 존재 시 항상 인증, 실패는 @RequireAuth 면 401 / 아니면 통과" 로 확장 + `LoginUserArgumentResolver` nullable(`parameter.isOptional`) 해석. `AuthInterceptorTest`(선택 인증 3케이스)·`LoginUserArgumentResolverTest`(필수/선택 분기) 갱신. Phase 7(행위): `ProductV1Controller`+`ProductV1ApiSpec`+`ProductV1Dto`(회원), `ProductV1ApiE2ETest`(목록 정렬·필터·페이징·soft-delete 제외 + 상세 likedByMe 4분기·404). Phase 8(행위): `ProductV1AdminController`+`ApiSpec`, salesStatus DTO 단 key 매핑, `ProductV1AdminApiE2ETest`(목록·상세·등록·수정·삭제·401) + `ProductV1DtoTest`(key↔enum 왕복). Phase 9(문서): api-spec 부록을 구현 완료 사실로 동기화. 전체 스위트 + ktlint Green.
- 2026-06-09: **Phase 6 완료(구조)** — 목록 `List → PageResult` 전환. `@Query` 없이 파생 쿼리(`findAllByBrandId(brandId, pageable)`) + `JpaRepository.findAll(Pageable)` + 어댑터 `findPage` 분기로 동적 brandId 필터 구현. tie-breaker `id desc` 고정. `GetProductsQuery` 합성 신설. ProductFacadeTest·ProductRepositoryImplIntegrationTest 갱신(메타 전파·`totalElements`/`totalPages` 검증). 전 테스트 + ktlint Green.
- 2026-06-09: interfaces 계층(Phase 5~9) 상세화 — brand plan 수준으로 재작성. Phase 5(관리자 인증 brand 재사용)·Phase 6(목록 PageResult 전환 + `GetProductsQuery` 합성, 구조)·Phase 7(회원 API: 선택 인증 — 헤더 존재 시 항상 인증 + nullable `@LoginUser`, 구조 선행 + 목록/상세 E2E)·Phase 8(관리자 API: salesStatus key 직렬화 구조 선행 + CRUD E2E)·Phase 9(api-spec 동기화). 회원 상세 선택 인증과 salesStatus 직렬화를 명시 결정으로 못박음.
