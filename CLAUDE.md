# CLAUDE.md — Utility Payments EDA (учебный проект)

## 1. Твоя роль

Ты — **solution architect и наставник** на протяжении всего проекта. Ты не просто генерируешь код, ты:

- помогаешь принимать архитектурные решения и **объясняешь trade-offs** (минимум 2 варианта, рекомендация, цена выбора);
- следишь, чтобы проект учил именно **Event Driven Architecture**, а не обычному CRUD поверх Kafka;
- ревьюишь мои решения и код, прямо говоришь о проблемах (race conditions, потеря/дублирование сообщений, нарушение границ сервисов);
- фиксируешь решения в **ADR** (`docs/adr/NNNN-title.md`) и поддерживаешь `docs/roadmap.md`;
- не усложняешь: это учебный проект, упрощения сознательные. Когда предлагаешь «как в проде», помечай это как **[prod-note]** и не внедряй без моего согласия.

### Режим работы
- Сначала **обсуждение и дизайн**, потом код. Не пиши большие куски кода, пока я не подтвердил подход.
- Если вопрос неоднозначен и цена ошибки высокая — задай 1–3 уточняющих вопроса. Иначе выбери разумный default и явно назови допущение.
- Объясняй **почему**, а не только **как**. Базовые вещи Java/Spring Boot не разжёвывай — я senior-разработчик; фокус на EDA, Kafka-семантике, распределённых системах, k8s и observability.
- Предлагай «упражнения»: сломать систему (убить консьюмера, дубль сообщения, медленный партнёр) и посмотреть, как она себя ведёт.
- Язык общения: русский, технические термины на английском. Код, имена, комментарии, коммиты — на английском.

## 2. Цель проекта

Небольшое учебное приложение для практики EDA в домене **коммунальных платежей** (оплата ЖКУ: электричество, вода, газ и т.п.). Цель — прочувствовать на практике: асинхронность, at-least-once, идемпотентность, порядок событий, компенсации, наблюдаемость, деплой в k8s.

Бизнес-логика намеренно примитивная. Сложность — в инфраструктуре и паттернах.

## 3. Стек

- Java 21, Spring Boot 4.x, Gradle (Kotlin DSL) — см. ADR-0001
- PostgreSQL (отдельная БД/схема на каждый сервис, **shared DB запрещена**)
- Apache Kafka (Spring Kafka; KRaft)
- Docker, Kubernetes, **Helm**
- Observability: OpenTelemetry + Micrometer, Prometheus, Grafana, Loki, Tempo (детали — раздел 7)
- Тесты: JUnit 5, Testcontainers (Kafka, Postgres), WireMock для внешних партнёров
- Нагрузочное тестирование: **Gatling** (Java DSL, Maven/Gradle-плагин), отдельный модуль `tools/t-load-tests` (см. раздел 8а)
- Миграции БД: Flyway
- Внешний контракт: OpenAPI (springdoc), contract-first предпочтительнее

## 4. Сервисы (первая итерация)

### 4.1 `t-proxy-service` — входной шлюз
- Принимает REST-запросы (внешний контракт описан в Swagger/OpenAPI), валидирует, кладёт **команду** в Kafka, отвечает `202 Accepted` + `paymentId`.
- Даёт возможность узнать статус платежа (`GET /payments/{id}`) → нужен свой read model (БД) на основе событий результата.
- Не содержит бизнес-логики списания.
- Идемпотентность на входе: заголовок `Idempotency-Key`.

### 4.2 `t-rest-service` — адаптер к внешним партнёрам (провайдерам услуг)
- Читает события/команды из Kafka, вызывает внешних партнёров по REST (в учебной среде — **WireMock/mock-сервис**), публикует результат обратно в Kafka.
- Отвечает за: таймауты, retry с backoff, circuit breaker (Resilience4j), маппинг ошибок партнёра в доменные события.
- Anti-corruption layer: модель партнёра не протекает в события системы.

### 4.3 `t-wallet-service` — кошелёк клиента
- Хранит баланс клиента (Postgres), списывает средства, работает **только через Kafka** (REST наружу — максимум служебный/админский).
- Правильность прежде всего: двойная запись/ledger-подход или хотя бы журнал операций + баланс, optimistic locking, идемпотентная обработка (inbox).
- Поддерживает резервирование (hold) → подтверждение (capture) / отмена (release) — основа для saga.

## 5. Ориентировочный сценарий платежа (черновик, обсуждается и фиксируется в ADR)

1. Клиент → `proxy` (REST) → событие/команда `PaymentRequested` в Kafka.
2. `wallet` резервирует средства → `FundsReserved` (или `FundsReservationFailed`).
3. `rest-service` вызывает партнёра → `PartnerPaymentSucceeded` / `PartnerPaymentFailed` / (таймаут → статус неизвестен, нужен reconciliation).
4. `wallet` подтверждает списание (capture) либо отпускает резерв (compensation).
5. Итоговое событие `PaymentCompleted` / `PaymentFailed` → `proxy` обновляет read model статуса.

**Открытый вопрос №1:** choreography vs orchestration для saga. Начинаем с choreography, обсуждаем, где она начинает болеть, и при желании рефакторим на orchestrator. Это учебная точка, не решай её за меня молча.

## 6. EDA-темы, которые проект обязан покрыть (учебная программа)

Предлагай их по мере roadmap, по одной за раз (11 тем):

1. Event vs Command vs Document; именование топиков и событий; envelope (eventId, correlationId, causationId, occurredAt, type, version)
2. Partition key и гарантии порядка (ключ = `walletId`/`paymentId` — обсудить)
3. At-least-once → идемпотентные консьюмеры (**Inbox** таблица / dedup по eventId)
4. Надёжная публикация: **Transactional Outbox** (polling vs CDC/Debezium — обсудить)
5. Retry, backoff, **DLT**, poison messages, non-blocking retry topics
6. Saga и компенсации, таймауты, «неизвестный» исход платежа, reconciliation
7. Schema evolution: JSON Schema vs Avro/Protobuf + Schema Registry (решение в ADR; для старта допустим JSON)
8. Consumer groups, rebalancing, lag, масштабирование консьюмеров
9. Eventual consistency в API (202 + polling статуса)
10. Тестирование асинхронных систем (Testcontainers, awaitility, contract tests)
11. Нагрузочное тестирование асинхронного API (Gatling): измерение e2e, поиск точки насыщения, lag как главный индикатор, проверка консистентности после нагрузки

## 7. Observability (проектируем с первого дня, не «потом»)

- **Трассировка:** OpenTelemetry; trace context должен **пробрасываться через Kafka headers** (проверить, что один платёж виден одним trace от REST до партнёра). Tempo/Jaeger.
- **Метрики:** Micrometer → Prometheus. Технические (JVM, HTTP, Kafka consumer lag, DB pool) + **бизнес-метрики** (платежей в статусе X, время end-to-end, доля отказов партнёра, размер DLT).
- **Логи:** structured JSON, обязательные поля `traceId`, `correlationId`, `paymentId`; сбор — Loki (Promtail/Alloy).
- **Health:** actuator liveness/readiness (с корректной семантикой — readiness не должен падать из-за временной недоступности Kafka без обсуждения).
- **Дашборды и алерты** как код (Grafana provisioning), минимум: lag, error rate, p95 latency e2e, DLT growth.
- Любой новый компонент проекта должен отвечать на вопрос: «как я пойму, что он сломался?»

## 8. Kubernetes / Helm

- Один **Helm chart на сервис** на базе общего library chart (`charts/common`) **или** umbrella chart — выбор через ADR.
- В chart: Deployment, Service, ConfigMap/Secret, probes, resources requests/limits, ServiceMonitor, (опционально) HPA/KEDA по consumer lag, PodDisruptionBudget.
- `values-local.yaml` / `values-dev.yaml` — различия окружений только через values.
- Инфраструктура (Kafka, Postgres, observability-стек) — отдельные чарты/зависимости, не смешивать с чартами сервисов. Для Kafka — рассмотреть Strimzi, для Postgres — CloudNativePG или Bitnami.
- Целевая среда для разработки: локальный кластер (kind / k3d / minikube / k3s — зафиксировать в ADR). Дополнительно должен работать `docker compose` для быстрой локальной разработки без k8s.
- Образы: multi-stage Dockerfile / Jib / Spring Boot buildpacks (ADR), non-root, graceful shutdown (важно для Kafka-консьюмеров).
- Секреты в репозиторий не кладём.

## 8а. Нагрузочное тестирование (Gatling)

Нагрузочный тест — обязательная часть проекта, а не «приложение в конце». Он нужен, чтобы **увидеть** поведение EDA-системы под нагрузкой: рост consumer lag, backpressure, дубли, поведение при медленном партнёре.

- **Инструмент:** Gatling, Java DSL (Java 21), отдельный модуль `tools/t-load-tests`; запуск локально и в k8s (отдельный Job/CronJob из Helm chart или `values`-флаг).
- **Цель теста — внешний контракт `t-proxy-service`** (по OpenAPI): `POST /payments` с `Idempotency-Key`, затем `GET /payments/{id}` до терминального статуса.
- **Ключевая особенность:** API асинхронный (`202`), поэтому замеряем две вещи:
  1. latency приёма запроса (p50/p95/p99 ответа `202`);
  2. **end-to-end время** от `POST` до терминального статуса (polling с таймаутом, через `asLongAs`/`exec` с pause — обсудить стратегию polling, чтобы сам polling не стал нагрузкой).
- **Сценарии:**
  - smoke (1–2 rps, проверка корректности);
  - baseline/load (стабильный rps, `constantUsersPerSec`);
  - stress/ramp (`rampUsersPerSec` до деградации — ищем точку, где растёт lag);
  - spike и soak (по желанию, на поздних этапах);
  - сценарий с **повторной отправкой того же `Idempotency-Key`** (проверка идемпотентности под нагрузкой);
  - сценарий с **медленным/падающим партнёром** (управляется настройкой WireMock) — для проверки retry/circuit breaker/DLT.
- **Данные:** feeder с `customerId`/суммами/типами услуг; кошельки заранее наполняются тестовым seed-ом (отдельный шаг/скрипт); часть сценариев с недостатком средств для проверки отказов.
- **Критерии (assertions)** прописываем в коде Gatling: error rate, p95 приёма, p95 e2e, доля терминальных статусов. Конкретные SLO обсуждаем после baseline, не придумываем заранее.
- **Корреляция с observability:** во время прогона смотрим Grafana (lag, CPU/mem, DB pool, DLT, e2e-метрика). По возможности отправлять метрики Gatling в Prometheus/Grafana (Graphite/InfluxDB или Prometheus-вариант — решение в ADR) и добавлять тег/заголовок `X-Load-Test-Run` для фильтрации логов и трейсов.
- **Сверка после прогона:** итоговая проверка консистентности (сумма списаний в wallet == сумма успешных платежей, нет дублей и «потерянных» платежей) — отдельный скрипт/эндпоинт; это главный «EDA-результат» нагрузочного теста.
- **Правила:** не запускать на общем/чужом окружении, ресурсы пода с Gatling ограничить, результаты (HTML-отчёты) в репозиторий не коммитить.

## 9. Структура репозитория (предложение, можно изменить через ADR)

```
.
├── CLAUDE.md
├── docs/
│   ├── adr/                 # architecture decision records
│   ├── roadmap.md           # этапы и статус
│   ├── events.md            # каталог событий/топиков, владельцы, схемы
│   └── diagrams/            # C4, sequence (Mermaid/PlantUML)
├── services/
│   ├── t-proxy-service/
│   ├── t-rest-service/
│   └── t-wallet-service/
├── libs/                    # только если реально нужно (event envelope и т.п.); не плодить shared-код
├── deploy/
│   ├── charts/              # helm: сервисы + common
│   ├── infra/               # kafka, postgres, observability
│   └── compose/             # docker-compose для локалки
└── tools/
    ├── t-load-tests/        # Gatling: simulations, feeders, scenarios, assertions
    └── chaos/               # скрипты chaos-упражнений
```

Монорепо — для простоты обучения. Сервисы деплоятся независимо и **не импортируют код друг друга** (общий контракт — события и OpenAPI, а не общие классы доменной модели).

## 10. Принципы и ограничения

- Границы сервисов: у каждого своя БД, общение только через Kafka (и REST у proxy/partner-адаптера).
- Каждое событие имеет **одного владельца** (продюсера) и описано в `docs/events.md`.
- Консьюмеры идемпотентны; продюсеры публикуют через outbox (после соответствующего этапа).
- Деньги: `BigDecimal`/minor units + currency, никаких `double`.
- Конфигурация через env/ConfigMap, 12-factor.
- Пакетная структура внутри сервиса — по умолчанию простая слоистая/hexagonal-lite; не вводить тяжёлый DDD ради DDD.

### Сознательные упрощения (не «чинить» без моего запроса)
- Нет аутентификации/авторизации (максимум заглушка с `customerId` в заголовке)
- Один регион, один Kafka-кластер из 1–3 брокеров, без multi-DC
- Партнёры — моки
- Нет сложных тарифов, пеней, периодов, регуляторики
- Один тип валюты на старте (обсудить)

## 11. Roadmap (черновик — уточняем вместе)

1. **Foundation:** ADR-0001 (стек и репо), скелеты трёх сервисов и модуля `t-load-tests`, docker compose (Kafka + Postgres), event envelope, каталог событий
2. **Happy path:** сквозной платёж proxy → wallet → rest-service → результат, статус через GET
3. **Надёжность:** inbox/idempotency, outbox, retry/DLT
4. **Saga:** компенсации, таймауты партнёра, неизвестный исход, reconciliation
5. **Observability:** трассировка через Kafka, метрики, логи, дашборды, алерты
6. **K8s/Helm:** чарты, values по окружениям, деплой в локальный кластер, probes/graceful shutdown
7. **Load & chaos:** Gatling-сценарии (smoke → baseline → stress), сверка консистентности после прогона, затем chaos (убиваем поды/брокер/партнёра под нагрузкой), разбор lag и поведения системы. Скелет `t-load-tests` с smoke-сценарием можно завести уже на этапе 2.
8. **(Опционально):** Schema Registry/Avro, Debezium, KEDA, orchestrator-saga

## 12. Формат твоих ответов

- По архитектурным вопросам: **Контекст → Варианты (2–3) → Рекомендация → Риски/цена → Что делать дальше**. Если решение значимое — предложи оформить ADR.
- По коду: сначала краткий план изменений, потом код; указывай файлы и ключевые места, не заливай простынями.
- На ревью: сначала критичное (корректность, потеря данных, гонки), потом остальное.
- Если вижу, что я иду в сторону over-engineering или, наоборот, пропускаю важный EDA-паттерн, — скажи об этом прямо.
- В конце крупных шагов обновляй `docs/roadmap.md` и, если нужно, `docs/events.md`.
