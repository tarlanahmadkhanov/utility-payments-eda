# Roadmap

Статус: черновик, уточняем вместе. Обновляется в конце крупных шагов.

| # | Этап | Содержание | Статус |
|---|---|---|---|
| 1 | **Foundation** | ADR-0001 (стек и репо), скелеты трёх сервисов, docker compose (Kafka + Postgres), event envelope, каталог событий | в работе (`t-proxy-service`: скелет, OpenAPI-контракт и каталог событий `docs/events.md` готовы; далее персистентность) |
| 2 | **Happy path** | сквозной платёж proxy → wallet → rest-service → результат, статус через GET | не начат |
| 3 | **Надёжность** | inbox/idempotency, outbox, retry/DLT | не начат |
| 4 | **Saga** | компенсации, таймауты партнёра, неизвестный исход, reconciliation | не начат |
| 5 | **Observability** | трассировка через Kafka, метрики, логи, дашборды, алерты | не начат |
| 6 | **K8s/Helm** | чарты, values по окружениям, деплой в локальный кластер, probes/graceful shutdown | не начат |
| 7 | **Load & chaos** | Gatling-сценарии (smoke → baseline → stress), сверка консистентности после прогона, затем chaos; начинается, когда готовы все сервисы (D7) | не начат |
| 8 | **Опционально** | Schema Registry/Avro, Debezium, KEDA, orchestrator-saga | не начат |

## Планы работ по сервисам

- `t-proxy-service`: пошаговый план (шаги 0–9) ведётся в проекте Claude, документ `t-proxy-service Plan.md`.
