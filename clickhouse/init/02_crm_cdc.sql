-- Поток изменений CRM в ClickHouse (Задание 4).
--
-- CRM больше не выгружается запросами из OLAP-контура: изменения таблицы clients
-- читает Debezium (логическая репликация PostgreSQL), отправляет в Kafka,
-- а ClickHouse принимает их через таблицу-движок Kafka.
-- Витрина отчётности собирается материализованными представлениями.

-- 1. Агрегаты телеметрии, которые пишет Airflow (без данных CRM).
CREATE TABLE IF NOT EXISTS reports.user_daily_telemetry
(
    login            String,
    report_date      Date,
    sessions         UInt32,
    movements        UInt32,
    avg_response_ms  Float32,
    max_response_ms  UInt32,
    errors           UInt32,
    min_battery      UInt8,
    updated_at       DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(report_date)
ORDER BY (login, report_date);

-- 2. Состояние клиентов CRM, полученное по CDC.
CREATE TABLE IF NOT EXISTS reports.crm_clients
(
    id               UInt64,
    login            String,
    full_name        String,
    email            String,
    prosthesis_model String,
    country          String,
    city             String,
    version          UInt64,
    updated_at       DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(version)
ORDER BY login;

-- 3. Таблица-движок Kafka: читает топик Debezium.
CREATE TABLE IF NOT EXISTS reports.crm_clients_kafka
(
    id               UInt64,
    login            String,
    full_name        String,
    email            String,
    prosthesis_model String,
    country          String,
    city             String,
    `__ts_ms`        UInt64,
    __deleted        String
)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'bionicpro.public.clients',
         kafka_group_name = 'clickhouse-crm-clients-v1',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

-- 4. Материализованное представление: Kafka -> таблица состояния клиентов.
CREATE MATERIALIZED VIEW IF NOT EXISTS reports.crm_clients_mv TO reports.crm_clients AS
SELECT id,
       login,
       full_name,
       email,
       prosthesis_model,
       country,
       city,
       `__ts_ms` AS version,
       now() AS updated_at
FROM reports.crm_clients_kafka
WHERE __deleted != 'true';

-- 5. Витрина отчётности: телеметрия протезов, обогащённая данными CRM.
CREATE TABLE IF NOT EXISTS reports.user_usage_mart
(
    login            String,
    report_date      Date,
    full_name        String,
    email            String,
    prosthesis_model String,
    country          String,
    sessions         UInt32,
    movements        UInt32,
    avg_response_ms  Float32,
    max_response_ms  UInt32,
    errors           UInt32,
    min_battery      UInt8,
    updated_at       DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(report_date)
ORDER BY (login, report_date);

-- 6. Витрина обновляется при загрузке телеметрии...
CREATE MATERIALIZED VIEW IF NOT EXISTS reports.user_usage_mart_mv TO reports.user_usage_mart AS
SELECT t.login            AS login,
       t.report_date      AS report_date,
       c.full_name        AS full_name,
       c.email            AS email,
       c.prosthesis_model AS prosthesis_model,
       c.country          AS country,
       t.sessions         AS sessions,
       t.movements        AS movements,
       t.avg_response_ms  AS avg_response_ms,
       t.max_response_ms  AS max_response_ms,
       t.errors           AS errors,
       t.min_battery      AS min_battery,
       now()              AS updated_at
FROM reports.user_daily_telemetry AS t
INNER JOIN reports.crm_clients AS c ON t.login = c.login;

-- ...и при изменении данных клиента в CRM: строки пользователя пересобираются.
CREATE MATERIALIZED VIEW IF NOT EXISTS reports.user_usage_mart_from_crm_mv TO reports.user_usage_mart AS
SELECT t.login            AS login,
       t.report_date      AS report_date,
       c.full_name        AS full_name,
       c.email            AS email,
       c.prosthesis_model AS prosthesis_model,
       c.country          AS country,
       t.sessions         AS sessions,
       t.movements        AS movements,
       t.avg_response_ms  AS avg_response_ms,
       t.max_response_ms  AS max_response_ms,
       t.errors           AS errors,
       t.min_battery      AS min_battery,
       now()              AS updated_at
FROM reports.crm_clients AS c
INNER JOIN reports.user_daily_telemetry AS t ON t.login = c.login;
