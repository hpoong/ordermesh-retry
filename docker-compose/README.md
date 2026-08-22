# Local Loki logging

IntelliJ에서 실행한 애플리케이션이 `LOG_DIR` 아래에 파일 로그를 남기면 Fluent Bit이 이를 읽어 Loki로 전송합니다.

```text
application file logs -> Fluent Bit -> Loki
```

## Run

`docker-compose/.env.example`을 `docker-compose/.env`로 복사한 뒤 로그 경로를 설정합니다. 기본 경로는 저장소 루트의 `logs`입니다.

```bash
docker compose -f docker-compose/docker-compose.yml up -d loki fluent-bit
```

Loki 준비 상태는 `http://localhost:3100/ready`에서 확인합니다. Fluent Bit 메트릭은 `http://localhost:2020/api/v1/metrics`에서 확인합니다.

## Labels

`job`은 공통 수집 라벨이고, `service`는 로그 경로 `/var/log/ordermesh-retry/<service>/...`에서 자동 추출됩니다.

새 서비스는 `/var/log/ordermesh-retry/<service>/` 아래에 로그만 남기면 Fluent Bit conf 수정 없이 수집됩니다.

현재 `account-service`와 `order-service`는 `dev` 또는 `prod` 프로필에서 `/var/log/ordermesh-retry/<service>`에 로그를 기록합니다. 이 경로를 그대로 사용할 경우 `LOG_DIR=/var/log/ordermesh-retry`로 설정하세요. 로컬 개발용 `logs/` 경로에 기록하도록 Logback 설정을 조정하는 작업은 별도로 필요합니다.
