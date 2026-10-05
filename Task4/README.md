# Задание 4. Повышение оперативности и стабильности работы CRM

Как устроено решение и в каких файлах смотреть детали.

## Как решена задача

Массовые выгрузки из CRM больше не выполняются запросами к OLTP-базе: изменения
таблицы `clients` читает Debezium через логическую репликацию PostgreSQL,
отправляет их в Kafka, а ClickHouse принимает поток через таблицу-движок Kafka.
Витрина отчётности собирается материализованными представлениями, поэтому
аналитика не влияет на транзакционную работу CRM.

## 1. CDC для базы CRM

* `docker-compose.yaml`: сервис `crm_db` запускается с
  `wal_level=logical`, `max_replication_slots=4`, `max_wal_senders=4` —
  без этого логическая репликация недоступна.
* `debezium/crm-connector.json`: конфигурация коннектора
  `io.debezium.connector.postgresql.PostgresConnector`:
  * `table.include.list = public.clients`, `plugin.name = pgoutput`;
  * слот репликации `bionicpro_crm`, публикация `bionicpro_publication`;
  * `snapshot.mode = initial` — начальный снимок таблицы;
  * трансформация `ExtractNewRecordState` разворачивает конверт Debezium,
    а `add.fields = op,ts_ms` добавляет в сообщение тип операции и время
    изменения (`__op`, `__ts_ms`) — это версия записи для витрины.
* `debezium/register-connector.sh`: идемпотентная регистрация коннектора
  через REST API Kafka Connect и ожидание состояния `RUNNING`.
  Запускается сервисом `debezium-register`.

## 2. Kafka

* `docker-compose.yaml`: `kafka` (apache/kafka:3.8.1, режим KRaft, порт 9092)
  и `kafka-connect` (quay.io/debezium/connect, порт 8083).
* Debezium создаёт топик `bionicpro.public.clients`; для наблюдения за потоком
  поднят `kafka-ui` на <http://localhost:8085>.

## 3. Приём данных в ClickHouse через KafkaEngine

* `clickhouse/init/02_crm_cdc.sql`:
  * `reports.crm_clients_kafka` — таблица-движок `Kafka`
    (`kafka_format = JSONEachRow`, группа `clickhouse-crm-clients-v1`,
    `kafka_skip_broken_messages = 100`);
  * `reports.crm_clients` — состояние клиентов, `ReplacingMergeTree(version)`,
    версия — `__ts_ms` из Debezium;
  * `reports.crm_clients_mv` — материализованное представление Kafka → состояние
    клиентов (удалённые записи отбрасываются по `__deleted`).

## 4. Витрина отчётности на MaterializedView

* `reports.user_daily_telemetry`: агрегаты телеметрии, которые пишет Airflow
  (данные CRM в ETL-процессе больше не выгружаются).
* `reports.user_usage_mart`: витрина отчётов.
* `reports.user_usage_mart_mv`: собирает витрину при загрузке телеметрии,
  объединяя её с данными клиентов.
* `reports.user_usage_mart_from_crm_mv`: пересобирает строки пользователя,
  когда приходит изменение клиента из CRM: витрина остаётся актуальной
  даже между запусками ETL.

## 5. Сервис отчётов на новой витрине

* `ReportService` читает `reports.user_usage_mart` (с `FINAL`, чтобы
  ReplacingMergeTree не вернул прошлые версии строк).
* `EtlStateCache` вместе с периодом витрины хранит версию данных CRM
  (`max(__ts_ms)`), а `ReportStorage` включает её в ключ объекта:
  изменение клиента в CRM автоматически инвалидирует готовый отчёт в S3/CDN.
* `airflow/dags/reports_mart_dag.py`: из процесса убрана задача выгрузки CRM
  (`extract_clients`), осталась только телеметрия: OLTP-база CRM не участвует
  в подготовке аналитики.

## Проверка потока изменений

```bash
docker compose up -d --build
docker compose exec airflow-scheduler airflow dags trigger bionicpro_reports_mart
```

Проверка живого потока изменений:

```bash
docker compose exec crm_db psql -U crm -d crm -c "UPDATE clients SET country='Германия' WHERE login='user1'"
docker compose exec clickhouse clickhouse-client --query "SELECT login, country, version FROM reports.crm_clients FINAL WHERE login='user1'"
docker compose exec clickhouse clickhouse-client --query "SELECT DISTINCT country FROM reports.user_usage_mart FINAL WHERE login='user1'"
```

Первая команда меняет данные в CRM, вторая показывает, что изменение доехало
по CDC, третья — что витрина пересобралась представлением без повторного запуска ETL.
