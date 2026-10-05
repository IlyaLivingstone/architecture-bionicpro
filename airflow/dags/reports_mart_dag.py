"""ETL-процесс подготовки витрины отчётности BionicPRO.

Источник: агрегаты телеметрии протезов из основной базы.
Приёмник: ClickHouse, таблица reports.user_daily_telemetry.
Данные CRM в этом процессе не выгружаются: изменения клиентов приходят
потоком CDC (Debezium → Kafka → KafkaEngine), а витрина для отчётов
собирается материализованными представлениями в ClickHouse.
"""
import os
from datetime import date, datetime, timedelta

import clickhouse_connect
import pandas as pd
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

CLICKHOUSE_HOST = os.environ.get("CLICKHOUSE_HOST", "clickhouse")
CLICKHOUSE_PORT = int(os.environ.get("CLICKHOUSE_PORT", 8123))
CLICKHOUSE_USER = os.environ.get("CLICKHOUSE_USER", "default")
CLICKHOUSE_PASSWORD = os.environ.get("CLICKHOUSE_PASSWORD", "")
CLICKHOUSE_DB = os.environ.get("CLICKHOUSE_DB", "reports")
WINDOW_DAYS = int(os.environ.get("REPORT_WINDOW_DAYS", 30))

TELEMETRY_TABLE = "user_daily_telemetry"
ETL_JOB = "reports_mart"

default_args = {
    "owner": "bionicpro",
    "depends_on_past": False,
    "retries": 2,
    "retry_delay": timedelta(minutes=5),
}


def extract_telemetry(**context):
    """Агрегация телеметрии по пользователю и дню за окно обработки."""
    hook = PostgresHook(postgres_conn_id="core_db")
    rows = hook.get_records(
        """
        SELECT login,
               event_time::date                      AS report_date,
               count(DISTINCT date_trunc('hour', event_time)) AS sessions,
               count(*)                              AS movements,
               round(avg(response_ms)::numeric, 1)   AS avg_response_ms,
               max(response_ms)                      AS max_response_ms,
               count(*) FILTER (WHERE error_code IS NOT NULL) AS errors,
               min(battery_level)                    AS min_battery
        FROM telemetry_events
        WHERE event_time >= now() - make_interval(days => %s)
        GROUP BY login, event_time::date
        ORDER BY login, report_date
        """,
        parameters=(WINDOW_DAYS,),
    )
    context["ti"].xcom_push(key="telemetry", value=[[str(value) for value in row] for row in rows])
    return len(rows)


def load_telemetry(**context):
    """Запись агрегатов телеметрии в ClickHouse; витрину собирают представления."""
    ti = context["ti"]
    telemetry = pd.DataFrame(
        ti.xcom_pull(key="telemetry", task_ids="extract_telemetry"),
        columns=["login", "report_date", "sessions", "movements",
                 "avg_response_ms", "max_response_ms", "errors", "min_battery"],
    )
    if telemetry.empty:
        return 0

    telemetry["report_date"] = pd.to_datetime(telemetry["report_date"]).dt.date
    for column in ["sessions", "movements", "max_response_ms", "errors", "min_battery"]:
        telemetry[column] = telemetry[column].astype(int)
    telemetry["avg_response_ms"] = telemetry["avg_response_ms"].astype(float)

    client = clickhouse_connect.get_client(
        host=CLICKHOUSE_HOST, port=CLICKHOUSE_PORT,
        username=CLICKHOUSE_USER, password=CLICKHOUSE_PASSWORD, database=CLICKHOUSE_DB,
    )
    first_date = min(telemetry["report_date"])
    # окно перезаписывается целиком — повторный запуск даёт тот же результат
    client.command(
        f"ALTER TABLE {TELEMETRY_TABLE} DELETE WHERE report_date >= toDate('{first_date}')"
    )
    client.insert_df(TELEMETRY_TABLE, telemetry)
    client.close()

    ti.xcom_push(key="last_period", value=str(max(telemetry["report_date"])))
    return len(telemetry)


def mark_etl_state(**context):
    """Фиксация периода, по который витрина подготовлена."""
    ti = context["ti"]
    last_period = ti.xcom_pull(key="last_period", task_ids="load_telemetry")
    rows = ti.xcom_pull(task_ids="load_telemetry") or 0
    if last_period is None:
        return None

    client = clickhouse_connect.get_client(
        host=CLICKHOUSE_HOST, port=CLICKHOUSE_PORT,
        username=CLICKHOUSE_USER, password=CLICKHOUSE_PASSWORD, database=CLICKHOUSE_DB,
    )
    client.insert("etl_state",
                  [[ETL_JOB, date.fromisoformat(str(last_period)), int(rows)]],
                  column_names=["job", "last_period", "rows_loaded"])
    client.close()
    return last_period


with DAG(
    dag_id="bionicpro_reports_mart",
    description="Агрегаты телеметрии для витрины отчётности; данные CRM приходят по CDC",
    default_args=default_args,
    start_date=datetime(2026, 1, 1),
    schedule="15 3 * * *",
    catchup=False,
    max_active_runs=1,
    tags=["bionicpro", "reports", "etl"],
) as dag:

    telemetry = PythonOperator(task_id="extract_telemetry", python_callable=extract_telemetry)
    load = PythonOperator(task_id="load_telemetry", python_callable=load_telemetry)
    state = PythonOperator(task_id="mark_etl_state", python_callable=mark_etl_state)

    telemetry >> load >> state
