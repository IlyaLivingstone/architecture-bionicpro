-- OLAP-база BionicPRO (ClickHouse).
-- Здесь хранятся состояние ETL и таблицы контура отчётности
-- (витрина собирается материализованными представлениями, см. 02_crm_cdc.sql).

CREATE DATABASE IF NOT EXISTS reports;

-- Состояние ETL: до какого периода данные уже подготовлены.
CREATE TABLE IF NOT EXISTS reports.etl_state
(
    job         String,
    last_period Date,
    rows_loaded UInt32,
    finished_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(finished_at)
ORDER BY job;
