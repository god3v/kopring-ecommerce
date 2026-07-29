# like 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 UC-1 ~ UC-3 을 포괄한다.
> 인증(헤더 인증·UNAUTHORIZED)은 **interfaces 레이어 책임** — Facade 는 "인증 통과한 호출(userId)" 로만 다룬다.

---

## 0. 스코프 / 결정

### 사용자 결정
- **likeCount SSOT** = `Product.likeCount` 비정규화 컬럼 토글 — 좋아요 등록/취소 시 ±1 트랜잭션 갱신. Like 행 `COUNT(*)` 집계는 사용하지 않는다.
- **likedByMe 연동** = 본 작업에 포함. `ProductFacade.getProductDetail` 이 `LikeRepository.existsByUserIdAndProductId` 로 실제 lookup. 시그니처 `loginId: String?` → `userId: Long?` 변경 (인증된 회원 식별 책임은 interfaces).
- **좋아요 취소** = **Hard delete** — `(userId, productId)` 행 자체 제거. Like 에는 soft delete / deletedAt 필드를 두지 않는다.

### 도메인 결정
- `Like(id, userId, productId, likedAt)` — surrogate id + `(userId, productId)` unique 제약.
- `likedAt` 은 도메인 객체의 필드 (`LocalDateTime`). infra 측 `BaseEntity.createdAt` 으로 매핑.
- 정렬 기준은 `likedAt desc`. 외부 응답에는 시각 노출 안 함.
- application 의존: `application.like` → `ProductRepository` (Product 검증 + likeCount 갱신).

---

## 1. Phase 1 — Domain (`com.loopers.domain.like`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 Like 생성
- [x] `Like.create(userId, productId)` 가 likedAt 을 now() 로 설정한다
- ~~userId/productId 양수 검증~~ — Facade 가 인증된 회원 식별자 + Product 존재 확인을 선행하므로 무용한 가드. 제거.

### 1.2 LikeErrorType
- [x] `LIKE_FORBIDDEN` (403) 보유. (BAD_REQUEST 는 도메인에서 던질 일이 없어 제거)

### 1.3 Product likeCount 토글 (Tidy First)
- [x] **구조 커밋**: `val likeCount: Long` → `var likeCount: Long; private set` 변경 (기존 테스트 모두 통과)
- [x] **행위 커밋**: `increaseLikeCount()` 호출 시 likeCount 가 1 증가한다
- [x] **행위 커밋**: `decreaseLikeCount()` 호출 시 likeCount 가 1 감소한다
- [x] **행위 커밋**: likeCount 가 0 일 때 `decreaseLikeCount()` 는 0 유지 (음수 방지 멱등)

---

## 2. Phase 2 — Application port

`LikeRepository`
- `save(like): Like`
- `findByUserIdAndProductId(userId, productId): Like?`
- `existsByUserIdAndProductId(userId, productId): Boolean`
- `delete(like)`
- `findAllByUserId(userId, page, size): List<Like>` — likedAt desc 고정

---

## 3. Phase 3 — Application Facade

mock 기반 단위 테스트. `LikeFacade` 가 `LikeRepository` + `ProductRepository` 의존.

### 3.A UC-1. `like(userId, productId)` — 멱등 등록
- [x] 존재하는 productId 로 신규 호출하면 Like 가 저장되고 `Product.likeCount` 가 1 증가한다
- [x] 이미 좋아요한 상태에서 재호출하면 추가 저장 없이 멱등 통과한다 (likeCount 변동 없음)
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

### 3.B UC-2. `unlike(userId, productId)` — 멱등 취소
- [x] 좋아요 행이 있으면 제거되고 `Product.likeCount` 가 1 감소한다
- [x] 좋아요가 없는 상태에서 호출하면 무동작 멱등 통과한다 (likeCount 변동 없음)
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

### 3.C UC-3. `getMyLikes(authedUserId, requestedUserId, page, size)` — 내 목록
- [x] 본인 식별자로 호출하면 좋아요한 상품 요약 목록이 반환된다
- [x] 좋아요한 상품이 없으면 빈 목록이 반환된다
- [x] 인증된 회원과 다른 식별자로 호출하면 `LIKE_FORBIDDEN` 예외가 발생한다
- [x] page / size 가 Repository 에 전달된다

---

## 4. Phase 4 — Infrastructure adapter

Testcontainers 통합 테스트 (`@DataJpaTest` + `MySqlTestContainersConfig`).

### 4.1 LikeRepositoryImpl
- [x] save 후 findByUserIdAndProductId 로 동일 도메인 객체가 복원된다
- [x] `(userId, productId)` 동일한 두 번째 save 는 unique 제약으로 실패한다
- [x] existsByUserIdAndProductId 가 정확히 판정한다
- [x] delete 가 행을 제거한다 (이후 findByUserIdAndProductId 가 null)
- [x] findAllByUserId 가 likedAt desc 로 정렬된다
- [x] findAllByUserId 에 page / size 페이징이 적용된다
- [x] 다른 user 의 좋아요는 결과에 포함되지 않는다

---

## 5. Phase 5 — Product 통합

### 5.1 ProductFacade likedByMe 연동 (구조 → 행위)
- [x] **구조 커밋**: `getProductDetail(productId, loginId: String?)` → `getProductDetail(productId, userId: Long?)` 시그니처 변경. 기존 테스트 갱신.
- [x] **행위 커밋**: userId 가 null 이면 likedByMe=false 로 응답한다
- [x] **행위 커밋**: userId 가 주어지면 `LikeRepository.existsByUserIdAndProductId` 결과를 likedByMe 로 응답한다

### 5.2 ProductEntity likeCount 동기화
- [x] `ProductEntity.syncFrom` 이 도메인의 likeCount 변경을 entity 컬럼에 반영한다 (Phase 4 ProductRepositoryImpl 통합에서 검증)

---

## 6. Phase 6 — interfaces.api.like + 페이징 (`PageResult<T>`)

> [api-spec.md](./api-spec.md) 의 3개 엔드포인트를 노출한다. Phase 1~5 (domain/application/infra/Product 연동) 는 모두 Green 이며, **비어있는 계층은 `interfaces.api.like` 뿐**이다.

### 6.0 스코프 / 결정

- **페이징 경계**: Spring `Pageable`/`Page` 는 **interfaces(Controller) 와 infrastructure(Adapter) 에만** 둔다. application·domain 은 프레임워크 독립 `PageResult<T>` 로 통신한다. (Spring Data 누수 차단 — domain/support Spring import 0 유지)
- **`PageResult<T>` 위치**: `com.loopers.support.page.PageResult` — domain 포트가 반환하므로 domain 이 의존 가능한 `support` 공유 커널에 둔다 (`support.error` 와 동일 위계, 순수 Kotlin).
- **인증 숫자 userId**: `LikeFacade` 는 `userId: Long` 으로 동작하나 현재 `@LoginUser` 는 `loginId: String` 만 주입한다. `AuthInterceptor` 가 이미 `authenticate()` 의 반환 `User` 를 버리고 있으므로 `user.id` 를 attribute 로 함께 저장하고, `@LoginUser id: Long` 도 해석하도록 resolver 를 확장한다 — **추가 DB 조회 0회**.
- **정렬**: 서버 고정 `likedAt desc` (requirements §5). `pageable.sort` 는 무시한다.
- **컨트롤러 형태**: 단일 `LikeV1Controller`. base path 가 둘(`/products/...`, `/users/...`) 이므로 클래스 레벨 `@RequestMapping` 없이 메서드별 풀 경로.
- **page/size 검증**: Pageable 사용 시 음수 page 는 Spring 이 0 으로 보정하므로 requirements §UC-3 E2 의 "음수 page → 400" 은 **구조적으로 발생 불가**(불변식 충족). size 상한은 `spring.data.web.pageable.max-page-size` 로 캡한다. → api-spec 의 400 행은 이 동작에 맞춰 정리.

### 6.1 PageResult 도입 + 읽기 경로 재배선 (구조 우선 — Tidy First)

> 외부 엔드포인트가 아직 없어 관측 가능한 행위 변화 없음. 기존 테스트를 새 반환형으로 갱신해 Green 유지하는 구조 커밋.

- [x] **(구조)** `support.page.PageResult<T>` 신설 — `content / page / size / totalElements / totalPages`. 순수 Kotlin, Spring import 0.
- [x] **(구조)** `LikeJpaRepository.findAllByUserId` 반환 `List<LikeEntity>` → `Page<LikeEntity>`. `LikeRepositoryImpl` 이 Spring `Page` → `PageResult<Like>` 변환 (PageRequest + `Sort` DESC `createdAt` 유지). `LikeRepository` 포트 반환 `List<Like>` → `PageResult<Like>`.
- [x] **(구조)** `LikeRepositoryImplIntegrationTest` 를 새 반환형으로 갱신 + `totalElements`/`totalPages` 정확성 검증 추가 (기존 정렬·페이징·격리 케이스 유지).
- [x] **(구조)** `LikeFacade.getMyLikes` 반환 `List<LikedProductResult>` → `PageResult<LikedProductResult>` (page 메타는 like 페이지에서 보존, content 는 기존 `findAllByIds` batch-load + `mapNotNull` 유지). `LikeFacadeTest` getMyLikes 케이스를 `.content` 기준으로 갱신 + 메타 전파 1건 추가.

> 위 3개는 포트 반환형 변경이 호출부(Facade)까지 컴파일상 연쇄되어 분리 불가 — 한 원자적 구조 단위로 처리. 6.1.1 의 `PageResult<T>` 가 read 경로(infra→domain→application)에 모두 배선됨.

### 6.1c 읽기 Query DTO 도입 (구조)

> 조건 검색(필터)이 들어올 유즈케이스(상품 검색 등)를 대비해, 유즈케이스별 Query 가 '조건의 집' 이 되고 페이징은 공용 VO 로 합성하는 패턴을 like 에서 먼저 세운다. 입력 계약 재구성 — 동작 불변(구조 커밋).

- [x] **(구조)** `support.page.PageQuery(page, size)` 신설 — `PageResult` 의 입력 짝(프레임워크 독립). 조건 검색 Query 가 합성해 페이징 어휘를 공유한다.
- [x] **(구조)** `application.like.query.GetMyLikesQuery(userId, paging: PageQuery)` 신설 — 조회 대상 + 페이징을 담는 유즈케이스 Query. 인증 주체(`authedUserId`)는 횡단 관심사라 분리.
- [x] **(구조)** `LikeFacade.getMyLikes(authedUserId, query: GetMyLikesQuery)` 로 시그니처 변경. 권한 검사 `authedUserId != query.userId` 유지. `LikeFacadeTest` 호출부 갱신.

### 6.2 인증 회원 주입 — `@LoginUser` + `AuthUser` 통합 (구조)

> **설계 (개정)**: "현재 인증 회원"을 단일 개념으로 통합한다. `@LoginUser` 1개로 통일하고, 주입 타입을 `loginId: String` 에서 `AuthUser(id, loginId)` VO 로 승격. 이전에 검토했던 별도 `@LoginUserId`(타입/애너테이션 2분할) 대신, 타입 매직 없고 개념이 단일한 VO 방식을 택한다. 도메인 `User` 를 그대로 노출하지 않고 컨트롤러가 필요한 식별자(id·loginId)만 좁혀 담는다. 기존 User 엔드포인트까지 손대는 구조 변경이나 동작은 불변(User E2E green 유지).

- [x] **(구조)** `interfaces.api.auth.AuthUser(id: Long, loginId: String)` 신설. `LoginUserArgumentResolver` 가 `@LoginUser` + `AuthUser` 타입을 해석(`ATTRIBUTE_AUTH_USER` attribute 반환, 없으면 `UNAUTHORIZED`). `LoginUserArgumentResolverTest` AuthUser 기준 갱신.
- [x] **(구조)** `AuthInterceptor` 가 `authenticate()` 반환 `User` 로 `AuthUser` 를 조립해 단일 attribute(`ATTRIBUTE_AUTH_USER`) 로 저장(기존 loginId/userId 2개 attribute 통합). `AuthInterceptorTest` 갱신(`User.id`/`loginId` 스텁 + AuthUser 주입 end-to-end).
- [x] **(구조)** 기존 사용처 마이그레이션 — `UserV1ApiSpec`/`UserV1Controller` 2곳(`@LoginUser loginId: String` → `user: AuthUser`, 호출부 `user.loginId`), `UserV1ControllerTest` stub 리졸버(AuthUser 반환). `UserV1ApiE2ETest` 동작 불변 통과.

### 6.3 LikeV1 API (행위 — Red → Green, E2E)

> 산출물: `LikeV1Dto`(`LikedProductsResponse` = content + page 메타, `LikedProductItem`), `LikeV1ApiSpec`(@Tag + 3 @Operation), `LikeV1Controller`. 검증은 `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` E2E.

**컨트롤러 시그니처** — base path 가 둘이라 클래스 레벨 매핑 없이 메서드별 풀 경로. 각 메서드에 `@RequireAuth` + `@LoginUser user: AuthUser` (현행 컨벤션, 짝으로).
- `like(@LoginUser user, @PathVariable productId)` → `likeFacade.like(user.id, productId)` → `ApiResponse.success()`
- `unlike(@LoginUser user, @PathVariable productId)` → `likeFacade.unlike(user.id, productId)` → `ApiResponse.success()`
- `getMyLikes(@LoginUser user, @PathVariable userId, @PageableDefault(size = 20) pageable)` → `GetMyLikesQuery(userId, PageQuery(pageable.pageNumber, pageable.pageSize))` → `likeFacade.getMyLikes(user.id, query)` → `LikedProductsResponse.from(result)`. 정렬은 서버 고정이므로 `pageable.sort` 미사용.

**E2E 셋업** — `@AfterEach` `DatabaseCleanUp`. 인증용 회원은 signup 엔드포인트로 생성 후 `UserRepository.findByLoginId(loginId)!!.id` 로 숫자 userId 확보. 대상 상품은 `productRepository.save(ProductFixture.validProduct(...)).id`. 인증 헤더 헬퍼(`X-Loopers-LoginId/Pw`). 403 케이스는 `userId + 1`(불일치 id) 로 — DB 추가 셋업 불필요(Facade 가 lookup 전에 분기).

**증분 경계** — UC 단위 3개 행위 커밋. 6.3a(POST)에서 Dto/ApiSpec/Controller 골격을 함께 만든다.

**리스크/확인** — ① `Pageable` MVC 리졸버는 Boot autoconfig 로 활성(커스텀 `WebMvcConfig` 는 기본 리졸버를 대체하지 않음) → E2E 로 확인. ② `ProductFixture` 의 `brandId=1` 저장 시 brand FK 가 없다는 가정(ID 참조 컨벤션) — 저장 실패 시 brand 셋업 추가. ③ `size` 상한은 필요 시 `spring.data.web.pageable.max-page-size` 로 캡.

**UC-1 등록 — `POST /api/v1/products/{productId}/likes`** (6.3a — 골격 포함)
- [x] 인증 회원이 등록하면 `200 OK` + `data: null`
- [x] 이미 좋아요한 상태 재요청도 `200 OK` (멱등)
- [x] 존재하지 않는 productId → `404 PRODUCT_NOT_FOUND`
- [x] 인증 헤더 누락 → `401 UNAUTHORIZED`

**UC-2 취소 — `DELETE /api/v1/products/{productId}/likes`** (6.3b)
- [x] 좋아요 취소 시 `200 OK` + `data: null`
- [x] 좋아요 없는 상태 요청도 `200 OK` (멱등)
- [x] 존재하지 않는 productId → `404 PRODUCT_NOT_FOUND`
- [x] 인증 헤더 누락 → `401 UNAUTHORIZED`

**UC-3 내 목록 — `GET /api/v1/users/{userId}/likes`** (6.3c)
- [x] 본인 식별자로 조회하면 `200 OK` + 페이지 봉투(content + totalElements/totalPages/page/size). likeCount 토글 반영(=1) 확인
- [x] 좋아요한 상품이 없으면 빈 content + 페이지 메타
- [x] 인증된 회원과 다른 `userId` → `403 LIKE_FORBIDDEN`
- [x] `page`/`size` 가 반영되고 `totalElements`/`totalPages` 가 정확 (3건/size 2 → 2페이지)
- [x] 인증 헤더 누락 → `401 UNAUTHORIZED`

> 정렬(likedAt desc)의 엄밀한 순서 검증은 타임스탬프 동률 flaky 회피를 위해 통합 테스트(`LikeRepositoryImplIntegrationTest`)가 담당. E2E 는 멤버십·페이지 메타 검증.

### 6.4 api-spec 동기화 (문서)

- [x] 내 목록 응답: 평면 배열 → **페이지 봉투**(`content` + `page`/`size`/`totalElements`/`totalPages`) 로 갱신. "페이지 메타" TBD 해소 표기.
- [x] 400 행: Pageable 의 음수 page 보정/ size 캡 동작에 맞춰 케이스 문구 정리(페이징 입력 400 제거 + 정규화 설명).

---

## 진행 로그

- 2026-05-29: plan 작성. likeCount SSOT = Product 컬럼 토글, likedByMe 실제 연동 포함, 취소 = hard delete 결정.
- 2026-05-29: Phase 1 ~ 5 전체 Green. Like 도메인 모델 · LikeFacade (10 tests) · LikeRepositoryImpl (7 통합 tests) · Product likeCount 토글 (3 tests) · ProductFacade.getProductDetail likedByMe 연동 (2 tests). 사용자 피드백 — Like 의 userId/productId 양수 검증은 무용해서 제거, LIKE_BAD_REQUEST 도 함께 제거.
- 2026-06-08: Phase 6 계획 추가. api-spec 3개 엔드포인트(`interfaces.api.like`) 가 유일한 미구현 계층. 결정 — 페이징은 `support.page.PageResult<T>` 로 application/domain 프레임워크 독립 유지, Spring `Pageable`/`Page` 는 Controller·Adapter 에만. 인증 숫자 userId 는 AuthInterceptor 가 보유한 `User.id` 를 attribute 로 저장 + `@LoginUser Long` resolver 확장으로 추가 조회 0회. 구조(6.1~6.2) → 행위(6.3) 커밋 분리.
- 2026-06-09: 6.1·6.1c·6.2·6.3 구현 완료(전체 스위트 Green). ① 읽기 경로 `PageResult<T>` 페이징 전환(infra Page→PageResult, domain/app 독립). ② 입력 합성 — `support.page.PageQuery` + `application.like.query.GetMyLikesQuery`. ③ 인증 주입은 사용자 결정으로 `@LoginUserId` 분리안 폐기 → `@LoginUser` + `AuthUser(id, loginId)` VO 단일 개념 통합(UserV1 마이그레이션, E2E 동작 불변). ④ `LikeV1Dto`/`LikeV1ApiSpec`/`LikeV1Controller`(`@RequestMapping("/api/v1")`, 각 메서드 `@RequireAuth`+`@LoginUser`) + E2E 13케이스. 남은 작업: 6.4 api-spec 문서 동기화.
