#!/bin/sh
# Полная выгрузка realm (пользователи, клиенты, LDAP-федерация, потоки, IdP)
# в keycloak/keycloak-results-export.json.
#
# Запускать на хосте из корня проекта:  sh keycloak/setup/export-realm.sh
set -eu

REALM="${KEYCLOAK_REALM:-reports-realm}"
TARGET="${EXPORT_PATH:-keycloak/keycloak-results-export.json}"
TMP_IN_CONTAINER="/tmp/realm-export.json"

docker compose exec -T keycloak /opt/keycloak/bin/kc.sh export --realm "$REALM" --file "$TMP_IN_CONTAINER"
docker compose cp "keycloak:$TMP_IN_CONTAINER" "$TARGET"
docker compose exec -T keycloak rm -f "$TMP_IN_CONTAINER"

echo "[export-realm] realm $REALM выгружен в $TARGET"
