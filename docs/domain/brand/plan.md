# brand 도메인 — TDD 진척 체크리스트

> [requirements.md](./requirements.md) UC-1~6 + [api-spec.md](./api-spec.md) 의 인터페이스 계약을 구현한다.
> Phase 1~4 (domain/application/infra) 는 **완료(Green)** — `product/plan.md` 에서 분리해 옮겨왔다.
> 남은 작업: **interfaces 계층 + 관리자 채널 인증(공유 인프라) + 목록 PageResult 전환** (Phase 5~9).
> 진행: `/tdd brand` 후 "go" → 위에서부터 첫 `- [ ]` 1개. Red→Green→Refactor, 구조/행위 커밋 분리.

---

## 0. 스코프 / 결정

- `brand` 는 별도 애그리거트 (`domain.brand` · `application.brand` · `infrastructure.brand`). Product 는 `brandId` 로 ID 참조.
- Repository 는 **soft-deleted 자원을 결과에서 제외** (`@SQLRestriction("deleted_at IS NULL")`) — 회원·관리자 모두 동일. 존재 확인에서 "찾을 수 없음".
- **관리자 인증**: 헤더 `X-Loopers-Ldap: loopers.admin`, `/api-admin/**` 경로 기준 `AdminAuthInterceptor` ([admin/logical-model.md](../admin/logical-model.md) v0.2). **공유 인프라 — product 재사용**. 기대값은 설정 외부화.
- **목록 페이징**: `PageResult<T>` 페이지 봉투(`like` 와 일관). `getBrandsForAdmin` → `PageResult<AdminBrandResult>`, 컨트롤러 `Pageable → PageQuery`(필터 없으므로 공용 `PageQuery` 직접 사용). api-spec 응답 동기화.
- **컨트롤러 분리**: 회원 `BrandV1Controller`(`/api/v1`) / 관리자 `BrandV1AdminController`(`/api-admin/v1`). 관리자 인증은 경로 기준 인터셉터가 담당.
- **HTTP**: 등록·수정 `200 OK` + 리소스, 삭제 `200 OK` + `data: null`.

---

## Phase 1 — Domain (`com.loopers.domain.brand`) — ✅ 완료

순수 Kotlin. Spring / JPA 의존 없음.

### 1.1 Brand 생성 불변식
- [x] Brand 를 blank name 으로 생성하면 예외가 발생한다

### 1.2 Brand soft delete + 업데이트
- [x] `softDelete()` 호출 시 `deletedAt` 이 설정된다
- [x] `softDelete()` 된 Brand 는 `isDeleted()` 가 true 다
- [x] `update(name)` 가 name 을 갱신한다

---

## Phase 2 — Application port (`BrandRepository`) — ✅ 완료

인터페이스 정의 (impl 검증은 Phase 4).

- `save(brand): Brand`
- `findById(id): Brand?` — soft-deleted 제외
- `findAll(page, size): List<Brand>` — soft-deleted 제외, 최신순 고정
- `existsByName(name): Boolean` — soft-deleted 제외 (유일성 검증)

---

## Phase 3 — Application Facade (`application.brand.BrandFacade`) — ✅ 완료

mock 기반 단위 테스트 (`BrandFacadeTest`). UC 번호는 [requirements.md](./requirements.md) 재정립(UC-1~6) 기준.

#### UC-1. `getBrand(brandId)` — 회원 단일 브랜드
- [x] 존재하는 brandId 로 호출하면 `BrandResult` 가 반환된다
- [x] 존재하지 않거나 삭제된 brandId 면 `BRAND_NOT_FOUND` 예외가 발생한다

#### UC-2. `getBrandsForAdmin(page, size)` — 관리자 목록
- [x] Repository.findAll 로 위임된다 (정렬 검증은 Phase 4)
- [x] page / size 가 Repository 에 전달된다
- [x] 등록된 브랜드가 없으면 빈 목록이 반환된다

#### UC-3. `getBrandForAdmin(brandId)` — 관리자 상세
- [x] 정상 응답이 반환된다 (`AdminBrandResult`)
- [x] 존재하지 않거나 삭제된 brandId 면 `BRAND_NOT_FOUND` 예외가 발생한다

#### UC-4. `registerBrand(command)` — 관리자 등록
- [x] 유효한 입력으로 등록하면 신규 Brand 가 저장된다
- [x] 동일 이름의 브랜드가 이미 있으면 `DUPLICATE_BRAND_NAME` 예외가 발생한다

#### UC-5. `updateBrand(brandId, command)` — 관리자 수정
- [x] 정상 입력으로 수정하면 name 이 갱신된다
- [x] 존재하지 않거나 삭제된 brandId 면 `BRAND_NOT_FOUND` 예외가 발생한다
- [x] 다른 브랜드와 이름이 중복되면 `DUPLICATE_BRAND_NAME` 예외가 발생한다 (자기 자신 제외)
- [x] 이름이 동일하면 중복 검사를 건너뛴다 (자기 자신 제외 보장)

#### UC-6. `deleteBrand(brandId)` — 카스케이드 삭제
- [x] 정상 호출 시 Brand 및 소속 Product 가 같은 트랜잭션에서 soft delete 된다
- [x] 존재하지 않거나 이미 삭제된 brandId 면 `BRAND_NOT_FOUND` 예외가 발생한다
- [x] 소속 Product 가 없으면 Brand 만 삭제된다

---

## Phase 4 — Infrastructure adapter (`BrandRepositoryImpl`) — ✅ 완료

Testcontainers 통합 테스트.

- [x] save 후 findById 로 동일 객체가 복원된다
- [x] findById 는 soft-deleted Brand 를 null 로 반환한다
- [x] findAll 이 createdAt desc + soft delete 제외 + 페이징을 적용한다
- [x] existsByName 가 soft-deleted 를 제외하고 판정한다

---

## Phase 5 — 관리자 채널 인증 (공유 인프라)

> `/api-admin/**` 전 경로 인증. product 도 그대로 재사용한다.

- [x] **(Red→Green)** `AdminAuthInterceptor` 신설 — `X-Loopers-Ldap` 헤더 검증. 단위 테스트(MockMvc standalone + 스텁 컨트롤러): ① 값이 `loopers.admin` 이면 통과 ② 헤더 누락 → `401 UNAUTHORIZED` ③ 값 불일치 → `401 UNAUTHORIZED`. 기대값은 설정 주입. (`CommonErrorType.UNAUTHORIZED` 추가)
- [x] **(구조)** `WebMvcConfig` 에 `addPathPatterns("/api-admin/**")` 로 등록 (회원 `AuthInterceptor` 와 공존). 기대값 설정 키 `loopers.admin.ldap-id` 추가(application.yml).

---

## Phase 6 — 관리자 목록 PageResult 전환 (구조 우선 — Tidy First)

> `like` 6.1 과 동형. 포트 반환형 변경이 Facade 까지 컴파일 연쇄되므로 한 원자적 구조 단위. 외부 엔드포인트 전이라 동작 불변(기존 테스트 갱신).

- [x] **(구조)** `BrandJpaRepository.findAllBy` 반환 `List` → `Page<BrandEntity>`. `BrandRepositoryImpl` 이 Spring `Page` → `PageResult<Brand>` 변환(정렬 `createdAt desc, id desc` 유지). `BrandRepository.findAll` 반환 `List<Brand>` → `PageResult<Brand>`. `BrandRepositoryImplIntegrationTest` 갱신 + `totalElements`/`totalPages` 검증 추가(`@SQLRestriction` 이 count 에도 적용 확인).
- [x] **(구조)** `BrandFacade.getBrandsForAdmin(page, size)` → `(pageQuery: PageQuery): PageResult<AdminBrandResult>`(메타 전파, `PageResult.map` 헬퍼 추가). `BrandFacadeTest` 해당 케이스를 `.content` 기준으로 갱신 + 메타 전파 검증.

---

## Phase 7 — 회원 브랜드 API (행위 — Red→Green, E2E)

> `GET /api/v1/brands/{brandId}`. 산출물: `BrandV1Controller`, `BrandV1ApiSpec`, `BrandV1Dto.BrandResponse{id,name}`. 인증 불필요.

- [x] 존재하는 브랜드를 조회하면 `200 OK` + `{ id, name }`
- [x] 존재하지 않거나 삭제 마크된 브랜드 → `404 BRAND_NOT_FOUND`

---

## Phase 8 — 관리자 브랜드 API (행위 — Red→Green, E2E)

> `/api-admin/v1/brands`. 산출물: `BrandV1AdminController`, ApiSpec, `BrandV1Dto`(`AdminBrandResponse`, `BrandsResponse`=페이지 봉투, `RegisterBrandRequest`, `UpdateBrandRequest`). 인증 헤더 `X-Loopers-Ldap`.

**UC-2 목록 — `GET /api-admin/v1/brands`**
- [x] 인증 관리자가 조회하면 `200 OK` + 페이지 봉투(content + page/size/totalElements/totalPages), **최신순**
- [x] 등록된 브랜드가 없으면 빈 content + 페이지 메타
- [x] `page`/`size` 가 반영되고 `totalElements`/`totalPages` 가 정확

**UC-3 상세 — `GET /api-admin/v1/brands/{brandId}`**
- [x] 존재하는 브랜드 → `200 OK` + `{ id, name }`
- [x] 미존재/삭제 마크 → `404 BRAND_NOT_FOUND`

**UC-4 등록 — `POST /api-admin/v1/brands`**
- [x] 정상 등록 → `200 OK` + 생성된 브랜드
- [x] 이름 형식 위반(blank) → `400 BRAND_BAD_REQUEST`
- [x] 이름 중복 → `409 DUPLICATE_BRAND_NAME`

**UC-5 수정 — `PUT /api-admin/v1/brands/{brandId}`**
- [x] 정상 수정 → `200 OK` + 갱신된 브랜드
- [x] 미존재/삭제 마크 → `404 BRAND_NOT_FOUND`
- [x] 변경 이름이 다른 브랜드와 중복(자기 자신 제외) → `409 DUPLICATE_BRAND_NAME`
- [x] 이름 형식 위반(blank) → `400 BRAND_BAD_REQUEST`

**UC-6 삭제 카스케이드 — `DELETE /api-admin/v1/brands/{brandId}`**
- [x] 삭제 시 `200 OK` + `data: null` — 브랜드와 **소속 상품들이 함께 삭제 마크** (E2E: 사전에 상품 생성 후, 삭제 뒤 `ProductRepository.findById` 가 null = 삭제 확인)
- [x] 미존재/이미 삭제 마크 → `404 BRAND_NOT_FOUND`

**관리자 인증 (전 엔드포인트 공통)**
- [x] `X-Loopers-Ldap` 헤더 누락 또는 값 불일치 → `401 UNAUTHORIZED` (목록 엔드포인트 E2E 로 헤더 누락 검증, 값 불일치는 `AdminAuthInterceptorTest`)

---

## Phase 9 — api-spec 동기화 (문서)

- [x] 관리자 목록(`§2`) 응답: 평면 배열 → `PageResult` 형태(`content` + `page`/`size`/`totalElements`/`totalPages`). 페이징 입력은 `Pageable` 정규화(음수 page→0, size 캡) 표기. 부록 메모도 갱신.

---

## 진행 로그

- 2026-05-29: (product/plan 통합 시절) brand 도메인·port·Facade(UC-3~8 = 현 UC-1~6)·infra 어댑터 전체 Green. soft delete 는 `@SQLRestriction(deleted_at IS NULL)`.
- 2026-06-09: soft-delete 조회 차단을 API 레벨로 명시 검증 추가 — 회원 상세·관리자 상세 = 삭제 브랜드 404, 관리자 목록 = 삭제 브랜드 제외. (동작은 `@SQLRestriction` 으로 이미 강제되던 것을 실제 soft-delete 데이터로 못박음. 회원 채널엔 브랜드 목록 엔드포인트 없음.)
- 2026-06-09: brand plan 을 `product/plan.md` 에서 분리. 완료된 Phase 1~4 를 옮기고(UC 번호를 brand requirements 재정립 기준 UC-1~6 으로 갱신), 남은 interfaces 계층 + 관리자 채널 인증(공유) + 목록 PageResult 전환을 Phase 5~9 로 수립.
