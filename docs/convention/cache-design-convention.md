# Cache Design Guide (조회 시 캐시 생성, 변경 시 무효화)

## 문서 범위
- 이 문서는 **전사 표준 캐시 설계 원칙**을 정의한다.
- 아래 "account-service 기준 구현 예시"는 표준을 현재 코드에 적용한 사례다.
- 즉, 메서드명 자체가 표준이 아니라 **패턴(조회 시 생성, 변경 시 무효화, 커밋 후 삭제)**이 표준이다.

## 목적
- 캐시는 조회 성능을 위한 보조 저장소로 사용하고, 쓰기 경로는 DB를 단일 진실 원천(Source of Truth)으로 유지한다.
- 생성(Create) 시 선캐시(pre-warm)하지 않고, 단건 조회(Read)에서만 캐시를 생성한다.
- 수정(Update) / 삭제(Delete) 시 캐시를 갱신하지 않고 무효화(evict)하여 stale data 위험을 줄인다.

## 기본 원칙
- Read-Through: 단건 조회에서 `cache hit -> 반환`, `cache miss -> DB 조회 후 cache put`.
- No Cache on Create: 생성 직후 캐시를 만들지 않는다.
- Evict on Write: 수정/삭제 성공 후 해당 키를 삭제한다.
- After Commit Evict: 트랜잭션 커밋 이후 이벤트 리스너에서 캐시를 제거한다.
- Cache Scope 최소화: 목록 조회(list)는 기본적으로 캐시하지 않는다. (복잡도 증가 방지)

## account-service 기준 구현 예시
- 조회: `UserService.getUser()` -> `UserRedisCacheService.getUserDetail()` -> miss 시 DB 조회 후 `putUserDetail()`.
- 생성: `createUser()`는 DB 저장만 수행, 캐시 생성 없음.
- 수정/삭제: `updateUser()`, `softDeleteUser()`에서 `UserDetailCacheEvictEvent` 발행.
- 캐시 삭제: `UserDetailCacheEvictListener`에서 `AFTER_COMMIT`으로 `evictUserDetail()` 수행.

참고 클래스:
- `account-service/src/main/java/com/hopoong/account/api/user/service/UserService.java`
- `account-service/src/main/java/com/hopoong/account/api/user/service/UserRedisCacheService.java`
- `account-service/src/main/java/com/hopoong/account/api/user/service/UserDetailCacheEvictListener.java`

## 작업 지시 템플릿 (복붙용)
아래 템플릿으로 요청하면 동일한 설계를 일관되게 적용할 수 있다.

```text
[캐시 설계 지시]
대상 도메인: <예: 사용자 상세>
키 패턴: <예: user:detail:{userId}>
TTL: <예: 120초>

요구사항:
1) 단건 조회에서만 캐시 생성(Read-Through)한다.
2) 생성(Create)에서는 캐시를 만들지 않는다.
3) 수정/삭제(Update/Delete) 성공 시 해당 키를 evict 한다.
4) evict는 트랜잭션 커밋 이후(AFTER_COMMIT) 이벤트 리스너에서 처리한다.
5) 목록 조회 캐시는 기본 제외한다. (필요 시 별도 논의)

산출물:
- Service: get/create/update/delete 캐시 흐름 반영
- CacheService: get/put/evict 메서드
- Event + Listener: <도메인>CacheEvictEvent, AFTER_COMMIT listener
- 테스트: 캐시 hit/miss, create no-cache, update/delete evict 검증
```

## 리뷰 체크리스트
- create 경로에 cache put이 없는가?
- get 단건에서 miss 시에만 cache put 하는가?
- update/delete에서 evict 이벤트를 발행하는가?
- 리스너가 `AFTER_COMMIT`으로 동작하는가?
- 예외 발생 시 캐시 계층 장애가 비즈니스 트랜잭션을 깨지 않게 처리했는가?
