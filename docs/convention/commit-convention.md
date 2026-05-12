# Commit Convention

Git 커밋 메시지 작성 규칙입니다.

## Purpose (목적)
- 커밋 로그만으로 변경 범위와 의도를 빠르게 파악한다.
- 모듈·기능 단위로 히스토리를 일관되게 유지한다.

## Format (형식)

```text
<type>(<scope>): [<area>] <summary>
```

- `type`: 변경 성격
- `scope`: 영향 모듈·레이어 (선택)
- `area`: 기능·설정 영역 태그 (선택)
- `summary`: 한 줄 요약 (명령형, 마침표 생략)

## Type (타입)

| type | 용도 |
|------|------|
| `feat` | 기능 추가, 설정·의존성·스키마 등 동작에 영향을 주는 변경 |
| `refactor` | 동작은 유지하고 구조·이름·정리만 바꾸는 변경 |
| `fix` | 버그·오동작 수정 |
| `docs` | 문서만 변경 (`docs/convention` 포함) |
| `chore` | 빌드·정리·잡무 (동작·문서 본문 변경 없음) |
| `test` | 테스트 추가·수정 |

## Scope (스코프)

- 저장소·모듈 단위로 쓴다. 예: `core-service`, `processing-service`, `order-service`
- 여러 모듈에 걸친 문서·공통 규칙은 scope를 생략할 수 있다.

## Area (영역 태그)

대괄호 `[]` 안에 변경이 속한 영역을 짧게 적는다.

| area | 예시 |
|------|------|
| `key` | `RedisKeys`, `RabbitMqKeys` 등 키·상수 정리 |
| `logs` | 로그·스택트레이스 출력 설정 |
| `build` | Gradle 의존성, 빌드 설정 |
| `flyway` | Flyway 마이그레이션·DB 정책 |
| `기능` | 위 태그로 묶기 어려운 일반 기능 작업 |

### Area 생략
- `docs` 타입: scope·area 없이 요약만 적는다.
- `chore`로 한정된 정리(불필요 파일 삭제 등): area를 생략한다.
- 그 외 `feat`·`refactor`·`fix`는 가능하면 area를 붙이고, 맞는 태그가 없으면 `기능`을 쓴다.

## Summary (요약)
- 한국어 또는 팀에서 통용되는 영문 짧은 구로 쓴다.
- 무엇을 바꿨는지 한 줄에 드러나게 쓴다.
- 본문(여러 `-m` 또는 빈 줄 아래)이 필요할 때만 상세·주의사항을 덧붙인다.

## Examples (예시)

```text
refactor(core-service): [key] RedisKeys inner class로 정리
feat(core-service): [logs] 스택트레이스 출력 설정 분리
feat(core-service): [build] add Redis dependency
feat(core-db): [flyway] login_id/email 유니크 정책 적용
docs: Redis value·RabbitMQ 키 컨벤션 문서 추가
chore(core-service): 불필요한 파일 정리
feat(core-service): [기능] 사용자 상세 캐시 조회 API 추가
```

## Review Checklist (리뷰 체크리스트)
- type이 변경 내용과 맞는가?
- scope가 실제 수정 모듈과 맞는가?
- area 규칙(생략·`기능` fallback)을 지켰는가?
- 한 커밋에 서로 다른 목적의 변경이 섞이지 않았는가?
