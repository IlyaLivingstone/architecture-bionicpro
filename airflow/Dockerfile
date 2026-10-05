FROM apache/airflow:2.9.3-python3.11

# clickhouse-connect нужен для загрузки витрины, postgres-провайдер — для источников.
RUN pip install --no-cache-dir \
    --constraint "https://raw.githubusercontent.com/apache/airflow/constraints-2.9.3/constraints-3.11.txt" \
    "apache-airflow-providers-postgres" \
    "clickhouse-connect==0.7.19" \
    "pandas==2.1.4"
