# Local Loki logging

IntelliJ에서 실행한 애플리케이션이 파일 로그를 남기면 Fluent Bit이 이를 읽어 Loki로 전송합니다.

```text
application file logs -> Fluent Bit -> Loki
```

## Run

```bash
docker compose -f docker-compose/docker-compose.yml up -d loki fluent-bit
```

Loki 준비 상태는 `http://localhost:3100/ready`에서 확인합니다. Fluent Bit 메트릭은 `http://localhost:2020/api/v1/metrics`에서 확인합니다.

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
