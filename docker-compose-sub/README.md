# docker-compose-sub — 로그 수집 및 Loki Label 가이드

`docker-compose-sub`는 **사내 중앙 Loki**로 로그를 전송하는 Fluent Bit 에이전트입니다.  
앱은 **파일 로그가 아닌 stdout/stderr**로 출력하고, Fluent Bit가 Docker 컨테이너 로그를 수집합니다.

```text
Spring Boot (ConsoleAppender)
  → stdout / stderr
  → Docker json-file 로그
  → Fluent Bit (docker-compose-sub)
  → Loki
  → Grafana
```

---

## Label 구조

Loki label은 **2단 계층**으로 사용합니다.

| 뎁스 | Loki label | 값 예시 | 출처 |
|------|------------|---------|------|
| 1뎁스 | `project` | `simple-retry` | `com.docker.compose.project` |
| 2뎁스 | `service` | `account` | `com.docker.compose.service` |

추가로 Fluent Bit가 붙이는 **고정 label**:

| label | 값 | 설명 |
|-------|-----|------|
| `collector` | `fluent-bit` | 수집기 종류 |
| `env` | `dev` | 환경 (필요 시 conf에서 변경) |

Grafana LogQL 예시:

```logql
{project="simple-retry"}
{project="simple-retry", service="account"}
{project="simple-retry", service="account"} |= "ERROR"
```

---

## 앱 설정 가이드

### 1. Spring Boot — stdout으로만 출력

컨테이너 환경에서는 **파일 Appender 없이 ConsoleAppender만** 사용합니다.

```xml
<!-- logback-spring.xml -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %5level %logger - %msg%n</pattern>
    </encoder>
</appender>

<root level="INFO">
    <appender-ref ref="CONSOLE" />
</root>
```

> 로컬 IntelliJ 실행 시 파일 로그가 필요하면 `dev` 프로필에서만 FILE Appender를 켜도 됩니다.  
> **Docker로 배포할 때는 stdout만** 남기세요.

### 2. Docker Compose — project 이름 지정

수집 대상은 `com.docker.compose.project=simple-retry` 인 컨테이너입니다.  
앱 compose 실행 시 **project 이름을 반드시 `simple-retry`로** 맞춥니다.

```bash
docker compose -p simple-retry up -d
```

또는 compose 파일에 project 이름을 고정합니다.

```yaml
# compose.yaml
name: simple-retry

services:
  account:
    image: account-service:latest
    # labels 수동 지정 불필요

  order:
    image: order-service:latest
```

`name: simple-retry` 또는 `-p simple-retry`를 쓰면 Docker가 자동으로 아래 label을 붙입니다.

```text
com.docker.compose.project = simple-retry   → Loki project
com.docker.compose.service = account        → Loki service
```

### 3. service 이름 규칙

`service` label은 **compose 파일의 service 키**가 그대로 사용됩니다.

```yaml
services:
  account:    # → service="account"
  order:      # → service="order"
  recovery:   # → service="recovery"
```

- 소문자, 하이픈(`-`) 사용 권장
- 서비스마다 이름을 다르게 유지 (중복 금지)
- `service` 이름을 자주 바꾸지 않기 (Grafana 대시보드·알람과 연동됨)

### 4. 별도 logging label은 필요 없음

이 구성에서는 앱 컨테이너에 `logging.job` 같은 **커스텀 label을 붙일 필요가 없습니다.**  
Docker Compose가 부여하는 `com.docker.compose.*` label만으로 충분합니다.

---

## Fluent Bit 실행

Fluent Bit는 **앱과 별도 project**로 실행합니다. (자기 로그를 수집하지 않도록 분리)

```bash
# 수집기 (한 번만 띄우면 됨)
docker compose -f docker-compose-sub/docker-compose.yml up -d

# 앱 (project 이름 주의)
docker compose -p simple-retry up -d
```

| 구분 | compose project | 비고 |
|------|-----------------|------|
| Fluent Bit | `docker-compose-sub` (폴더명 기본값) | 수집기 |
| 앱 | `simple-retry` | 로그 발생 대상 |

---

## Label이 만들어지는 과정

```text
1. 앱이 stdout에 로그 출력
2. Docker가 json-file 로그로 저장
      /var/lib/docker/containers/<id>/<id>-json.log
3. Fluent Bit tail input이 로그 파일 읽기
4. docker filter가 Docker API로 컨테이너 메타데이터 조회
      container_label_com_docker_compose_project
      container_label_com_docker_compose_service
5. grep filter로 project=simple-retry 만 통과
6. modify filter로 Loki label 필드 생성
      project ← com.docker.compose.project
      service ← com.docker.compose.service
7. Loki output 전송
      Labels: collector, env (고정)
      Label_Keys: project, service (동적)
```

관련 설정 파일:

```text
docker-compose-sub/
├── docker-compose.yml
└── fluent-bit/
    ├── fluent-bit.conf
    ├── parsers.conf
    └── jobs/
        └── simple-retry.conf   # 수집·필터·Loki label 정의
```

---

## 새 서비스 추가 시

새 마이크로서비스를 추가할 때 **Fluent Bit 설정 변경은 필요 없습니다.**

1. compose에 service 추가

```yaml
# compose.yaml (name: simple-retry)
services:
  payment:
    image: payment-service:latest
```

2. Spring Boot는 stdout 로그만 사용
3. `docker compose -p simple-retry up -d` 로 재기동

→ 자동으로 `project=simple-retry`, `service=payment` 로 수집됩니다.

---

## Label로 쓰면 안 되는 것

Loki label은 **카디널리티가 낮은 값**만 사용합니다.

| 사용 가능 | 사용 금지 |
|-----------|-----------|
| `project`, `service`, `env` | `request_id`, `trace_id`, `user_id` |
| `collector` | `order_id`, IP, URL path |

요청 단위 식별자는 **로그 본문** 또는 JSON structured field에 넣으세요.

```text
# 좋음
{project="simple-retry", service="account"} | json | request_id="abc-123"

# 나쁨 — label로 request_id 사용 (Loki 성능 저하)
{request_id="abc-123"}
```

---

## 점검 방법

### 1. 컨테이너 label 확인

```bash
docker inspect <container_id> --format '{{json .Config.Labels}}' | jq
```

기대값:

```json
{
  "com.docker.compose.project": "simple-retry",
  "com.docker.compose.service": "account"
}
```

### 2. Fluent Bit 상태 확인

```bash
curl http://localhost:2020/api/v1/health
curl http://localhost:2020/api/v1/metrics/prometheus
```

### 3. Grafana에서 label 확인

Explore → Loki → Label browser

```text
project  = simple-retry
service  = account | order | ...
collector = fluent-bit
env      = dev
```

### 4. 로그가 안 보일 때

| 증상 | 확인 사항 |
|------|-----------|
| 아무 로그도 없음 | 앱이 `docker compose -p simple-retry`로 실행됐는지 |
| 특정 서비스만 없음 | compose service 이름·컨테이너 기동 상태 확인 |
| Fluent Bit 오류 | `docker logs simple-retry-fluent-bit` |
| project label 불일치 | `-p` 옵션 또는 `name:` 이 `simple-retry`인지 |
| stdout 미출력 | logback에 FILE만 있고 CONSOLE이 없는지 |

---

## 다른 project를 수집하려면

현재 `simple-retry.conf`는 **project=`simple-retry`만** 수집합니다.  
다른 project를 추가하려면:

1. `fluent-bit/jobs/<project-name>.conf` 복사·수정
2. `grep`의 `container_label_com_docker_compose_project` 값 변경
3. `fluent-bit.conf`에 `@INCLUDE jobs/<project-name>.conf` 추가

---

## 요약 체크리스트

앱 팀 배포 전 확인:

- [ ] Logback `ConsoleAppender` 사용 (Docker 실행 시)
- [ ] `docker compose -p simple-retry` 또는 `name: simple-retry` 사용
- [ ] compose `services` 키가 의도한 `service` label 이름인지 확인
- [ ] Fluent Bit(`docker-compose-sub`)가 호스트에서 기동 중인지 확인
- [ ] Grafana에서 `{project="simple-retry", service="<서비스명>"}` 조회 가능한지 확인
