# Задание 2. Разработка сервиса отчётов

Разбор по пунктам задания со ссылками на файлы.

## Задача 1. Архитектура подготовки и получения отчётов

`BionicPRO_C4_reports_to-be.drawio` (+ PNG) — контур отчётности в модели C4:

* источники: телеметрия протезов (PostgreSQL) и данные клиентов CRM (CRM DB);
* Airflow по расписанию объединяет их и перезаписывает витрину в ClickHouse;
* сервис `bionicpro-api` читает готовую витрину и отдаёт отчёт;
* личность пользователя подтверждает `bionicpro-auth` по серверной сессии.

## Задача 2. Airflow DAG

* `airflow/dags/reports_mart_dag.py`: DAG `bionicpro_reports_mart`,
  расписание `15 3 * * *`, окно обработки 30 дней (переменная `REPORT_WINDOW_DAYS`):
  2. `extract_telemetry` — агрегация телеметрии по пользователю и дню
     (сессии, движения, среднее и максимальное время отклика, ошибки, минимальный заряд);
  3. `load_telemetry` — запись агрегатов в `reports.user_daily_telemetry`
     (с Задания 4 данные CRM приходят по CDC, а витрину собирают представления);
  4. `mark_etl_state` — фиксация периода, по который витрина подготовлена.
* `clickhouse/init/01_schema.sql`: база `reports` и таблица состояния
  `reports.etl_state`; таблицы витрины описаны в `clickhouse/init/02_crm_cdc.sql`
  (в Задании 4 витрина `reports.user_usage_mart` собирается материализованными
  представлениями из телеметрии и данных CRM, пришедших по CDC).
  Движок `ReplacingMergeTree`, ключ сортировки `(login, report_date)` — быстрый
  доступ к данным пользователя.
* `airflow/Dockerfile`: образ Airflow с `clickhouse-connect` и провайдером postgres.
* Источники для стенда: `core/init/01_schema.sql` (телеметрия протезов)
  и `crm/init/01_schema.sql` (клиенты CRM).

## Задача 3. API отчётов

* `bionicpro-api/`: сервис на Java 21 + Spring Boot, эндпоинты:
  * `GET /reports` — отчёт текущего пользователя в CSV (период можно сузить
    параметрами `from` и `to`);
  * `GET /reports/availability` — по какую дату витрина подготовлена и сводка по отчёту.
* `ReportService` формирует единственный SQL-запрос к ClickHouse
  (`... FROM user_daily_usage WHERE login = ...`); вычислений в реальном времени нет.
* `ClickHouseClient` ходит в OLAP по HTTP-интерфейсу, соединение с БД напрямую не открывается.

## Задача 4. Ограничение доступа

* `SessionAuthFilter` требует серверную сессию и берёт пользователя из неё;
  логин в запросе не участвует, поэтому чужой отчёт запросить нельзя.
* `bionicpro-auth` отдаёт данные сессии только внутренним сервисам —
  по общему секрету `X-Internal-Token` (`InternalController`).
* Без cookie сессии сервис отвечает `401`, с чужими параметрами `login` —
  по-прежнему отдаёт отчёт владельца сессии.

## Задача 5. Кнопка в UI

* `frontend/src/components/ReportPage.tsx`: кнопка «Download Report» вызывает
  `GET /reports` с cookie сессии, скачивает CSV и показывает сводку по доступному периоду;
  ошибки сервиса (в том числе необработанный период) выводятся пользователю текстом.

## Чек-лист «Перед отправкой задания»

| Требование | Как выполнено |
| --- | --- |
| UI вызывает API генерации отчётов | кнопка «Download Report» → `GET /reports` |
| Отчёт нельзя получить без аутентификации | `SessionAuthFilter` → `401` |
| Авторизованный пользователь получает только свой отчёт | логин берётся из сессии, параметры запроса игнорируются |
| Отчёт запрашивается из OLAP | `ReportService` → `ClickHouseClient` → `reports.user_daily_usage` |
| Отчёт только за период, обработанный Airflow | период сверяется с `reports.etl_state`, иначе `409` с подсказкой |

## Проверка

```bash
docker compose up -d --build
docker compose exec airflow-scheduler airflow dags trigger bionicpro_reports_mart
curl -s --data-binary "SELECT * FROM reports.user_daily_usage LIMIT 5" http://localhost:8123/
```

Далее открыть <http://localhost:3000>, войти (`user1` / `password123`) и нажать
«Download Report» — скачается CSV с отчётом по своему протезу. Запрос
`http://localhost:8000/reports` без входа вернёт `401`, а запрос с параметром
`?from=<завтрашняя дата>` — `409` и сообщение, по какую дату данные готовы.
