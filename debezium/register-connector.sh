#!/bin/sh
# Регистрация коннектора Debezium в Kafka Connect.
# Скрипт идемпотентный: повторный запуск обновляет конфигурацию коннектора.
set -eu

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
CONFIG_FILE="${CONFIG_FILE:-/debezium/crm-connector.json}"
NAME=$(jq -r '.name' "$CONFIG_FILE")

echo "[debezium] регистрирую коннектор $NAME в $CONNECT_URL"

if curl -sf "$CONNECT_URL/connectors/$NAME" >/dev/null 2>&1; then
  curl -s -X PUT -H 'Content-Type: application/json' \
    -d "$(jq -c '.config' "$CONFIG_FILE")" \
    "$CONNECT_URL/connectors/$NAME/config" >/dev/null
  echo "[debezium] конфигурация коннектора обновлена"
else
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "$(cat "$CONFIG_FILE")" \
    "$CONNECT_URL/connectors" >/dev/null
  echo "[debezium] коннектор создан"
fi

i=0
until [ "$(curl -s "$CONNECT_URL/connectors/$NAME/status" | jq -r '.connector.state')" = "RUNNING" ]; do
  i=$((i + 1))
  if [ "$i" -gt 40 ]; then
    echo "[debezium] коннектор не перешёл в RUNNING:"
    curl -s "$CONNECT_URL/connectors/$NAME/status" | jq '.tasks'
    exit 1
  fi
  sleep 3
done

echo "[debezium] коннектор $NAME в состоянии RUNNING"
