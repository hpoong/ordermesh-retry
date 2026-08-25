# Local Observability (Loki / Tempo)

## Logging

IntelliJ에서 실행한 애플리케이션이 파일 로그를 남기면 Fluent Bit이 이를 읽어 Loki로 전송합니다.

```text
application file logs -> Fluent Bit -> Loki
```

## Tracing (Tempo)

앱이 OTLP HTTP로 Tempo에 span을 보냅니다. Grafana datasource 연동은 이후 작업입니다.

```text
order-service (HTTP + Outbox 발행)
  -> RabbitMQ
processing-service (consume)
  -> account-service (HTTP)
  -> 실패 시 recovery-service (ingest)
```

```text
앱 (Micrometer Tracing / OTel) -> Tempo :4318/v1/traces
Tempo API -> :3200
```

로컬 기본값:

- `management.otlp.tracing.endpoint=http://localhost:4318/v1/traces`
- sampling `1.0` (개발용 전량 수집)
- 환경변수 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`로 덮어쓸 수 있음

## Run

```bash
# 로그 수집
docker compose -f docker-compose/docker-compose.yml up -d loki fluent-bit

# 트레이스 저장소
docker compose -f docker-compose/docker-compose.yml up -d tempo
```

준비 상태 확인:

- Loki: `http://localhost:3100/ready`
- Tempo: `http://localhost:3200/ready`
- Fluent Bit 메트릭: `http://localhost:2020/api/v1/metrics`

## Tempo ports

| Port | 용도 |
|------|------|
| 3200 | Tempo HTTP API (`/ready`, 조회) |
| 4317 | OTLP gRPC (앱 계측 시 사용) |
| 4318 | OTLP HTTP (앱 계측 시 사용) |

설정 파일: `tempo/config.yml`  
트레이스 보관: 로컬 볼륨 `rdermesh-retry-tempo-volumes` (기본 retention 168h)

## Config layout

Fluent Bit 설정은 Loki `job` 단위로 분리되어 있습니다.

```text
fluent-bit/
├── fluent-bit.conf          # main: [SERVICE] + @INCLUDE
├── parsers.conf             # job별 parser
└── jobs/
    └── ordermesh-retry.conf # job=ordermesh-retry 파이프라인
```

새 job 추가 시:

1. `jobs/<job-name>.conf` 생성 (INPUT / FILTER / OUTPUT)
2. `parsers.conf`에 해당 job용 parser 추가
3. `fluent-bit.conf`에 `@INCLUDE jobs/<job-name>.conf` 추가

## Log directory

호스트 `docker-compose/logs/`는 컨테이너 `/var/log/`에 마운트됩니다. job별로 하위 디렉터리를 둡니다.

```text
logs/
└── ordermesh-retry/
    ├── account/
    └── order/
```

## Labels

`job`은 job conf의 Loki label이고, `service`는 로그 경로 `/var/log/<job>/<service>/...`에서 자동 추출됩니다.

`ordermesh-retry` job 아래 새 서비스는 `/var/log/ordermesh-retry/<service>/`에 로그만 남기면 job conf 수정 없이 수집됩니다.

현재 `account-service`와 `order-service`는 `dev` 또는 `prod` 프로필에서 `/var/log/ordermesh-retry/<service>`에 로그를 기록합니다. 로컬 개발용 `docker-compose/logs/ordermesh-retry/<service>/` 경로에 기록하도록 Logback 설정을 조정하는 작업은 별도로 필요합니다.
