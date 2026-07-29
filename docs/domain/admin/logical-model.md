# 관리자(Admin) 채널 & 인증 설계

> **상태**: v0.2 (2026-06-09)
> **변경 이력**:
> - v0.2 (2026-06-09): v0.1 의 "별도 `admin` 계정 aggregate" 결정을 **폐기**한다. 관리자는 도메인 모델·자격 저장소를 두지 않고, **헤더(`X-Loopers-Ldap`)로 식별**한다. requirements §1 의 "관리자는 외부 액터이며 도메인 모델로 등장하지 않는다" 서술과 다시 일치한다.
> - v0.1 (2026-06-09): (폐기) 별도 admin 계정 aggregate + 자격증명 헤더 안.
>
> **범위**: 관리자 채널(`/api-admin`)의 식별·인증을 정의한다. 관리자 *동작*(브랜드·상품 CRUD)은 [product/requirements.md](../product/requirements.md)·[brand/requirements.md](../brand/requirements.md) 와 두 api-spec 이 정의한다.
> **파일명 주의**: 관리자는 데이터 모델(aggregate/테이블)을 갖지 않으므로 본 문서에 "논리 데이터 모델" 은 없다 — 채널과 인증 메커니즘만 다룬다. (파일명은 api-spec 참조 호환을 위해 유지)

---

## 1. 결정 요약

| 항목 | 결정 |
|---|---|
| 관리자 표현 | **도메인 모델 없음** — 별도 aggregate·계정 테이블·자격 저장소를 두지 않는다. 외부 액터. |
| 채널 | `/api-admin/v1` prefix 로 관리자 기능 제공. 회원 `/api/v1` 과 분리. |
| 식별/인증 | 헤더 **`X-Loopers-Ldap: loopers.admin`** 으로 관리자를 식별한다. |
| 실패 | 헤더 누락 또는 값 불일치 → `401 UNAUTHORIZED`. |

---

## 2. 채널

- `/api-admin/v1/**` = 관리자 전용 경로 묶음. 회원 카탈로그(`/api/v1`)와 노출 경로·인증 방식이 분리된다.

---

## 3. 식별 헤더

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 관리자 식별자 |

> 값(`loopers.admin`)은 설정값으로 외부화할 수 있다.

---

## 4. 인증 흐름 (`AdminAuthInterceptor`)

`/api-admin/**` 의 모든 요청에 대해 (경로 기준 — 메서드 애너테이션 불요):

1. `X-Loopers-Ldap` 헤더의 존재를 확인한다. 누락 → `401 UNAUTHORIZED`.
2. 헤더 값이 기대값(`loopers.admin`)과 일치하는지 확인한다. 불일치 → `401 UNAUTHORIZED`.
3. 통과하면 본 흐름으로 진입한다. 관리자 식별자(id 등)는 없다 — "관리자다" 라는 **사실만** 컨텍스트에 둔다.

> 회원 채널은 애너테이션 기준(`@RequireAuth`)이지만, 관리자 채널은 전 경로가 인증 필수이므로 경로 패턴 기준 인터셉터가 단순하고 누락 위험이 없다.

---

## 5. 컴포넌트 매핑 (구현 시)

| 계층 | 컴포넌트 | 비고 |
|---|---|---|
| `interfaces.api.auth` | `AdminAuthInterceptor` | `X-Loopers-Ldap` 헤더 검증. 기대값은 설정 주입 |
| `interfaces.api.config` | `WebMvcConfig` 에 `addPathPatterns("/api-admin/**")` 로 등록 | |
| `support.error` / `interfaces` | 관리자 인증 실패 에러 — 코드 `UNAUTHORIZED` (HTTP 401) | 전용 `AdminErrorType.UNAUTHORIZED` 또는 공통 에러 재사용(구현 결정) |
| domain / application / infrastructure | **컴포넌트 없음** | 관리자 데이터 모델·Facade·Repository 가 필요 없다 |

> 회원 인증과 달리 주체 주입(`@LoginUser` 대응) 컴포넌트가 필요 없다 — 관리자에는 식별자가 없고 권한 사실만 있기 때문이다. 감사 로그 등으로 행위자 추적이 필요해지면 헤더에서 식별 정보를 확장한다.

---

## 6. 요구사항 정합

- 본 설계는 requirements §1 의 "**관리자는 외부 액터이며 도메인 모델로 등장하지 않는다**" 와 일치한다. (v0.1 의 계정 aggregate 안은 폐기되었고, requirements 의 원 서술이 그대로 유효하다.)
- 두 api-spec 의 `§0.2 관리자 채널 인증` 은 본 문서의 헤더(`X-Loopers-Ldap`)를 참조한다.

---

## 7. 보안 주의 / 후속

- **신뢰 경계 전제**: `X-Loopers-Ldap` 는 외부 클라이언트가 임의로 주입할 수 없어야 한다 — 헤더는 신뢰 경계(게이트웨이 / LDAP 연동 / 사내망)에서 설정·검증되고, 애플리케이션이 그 경계 뒤에 있다는 전제에서만 안전하다. 공개 인터넷에 직접 노출되는 구성에서는 이 헤더만으로 인가를 보장할 수 없다.
- **권한 등급**: 현재 단일 관리자 권한. 등급/세분 권한이 필요해지면 헤더 값 또는 별도 클레임으로 확장.
- **기대값 외부화**: `loopers.admin` 비교값은 설정(`application*.yml` / 환경변수)으로 분리.
