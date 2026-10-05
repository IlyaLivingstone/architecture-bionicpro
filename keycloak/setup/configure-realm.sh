#!/bin/sh
# Настройка realm `reports-realm` после импорта:
#   1) федерация пользователей из OpenLDAP представительства;
#   2) маппинг LDAP-групп на роли realm (user / prothetic_user / administrator);
#   3) обязательный OTP для всех пользователей;
#   4) права сервисной учётной записи bionicpro-auth на чтение пользователей;
#   5) подстановка реальных ключей Яндекс ID, если они переданы в окружении.
# Скрипт идемпотентный: повторный запуск не создаёт дубли.
set -eu

KC_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
REALM="${KEYCLOAK_REALM:-reports-realm}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
LDAP_URL="${LDAP_URL:-ldap://ldap:389}"
LDAP_BIND_DN="${LDAP_BIND_DN:-cn=admin,dc=example,dc=com}"
LDAP_BIND_PASSWORD="${LDAP_BIND_PASSWORD:-admin}"
LDAP_USERS_DN="${LDAP_USERS_DN:-ou=People,dc=example,dc=com}"
LDAP_GROUPS_DN="${LDAP_GROUPS_DN:-ou=Groups,dc=example,dc=com}"

BODY=/tmp/kc-body.json
log() { echo "[configure-realm] $*"; }

# --- ожидание Keycloak ------------------------------------------------------
log "ожидание Keycloak на $KC_URL"
i=0
until curl -sf "$KC_URL/realms/master" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 120 ]; then
    log "Keycloak не поднялся"
    exit 1
  fi
  sleep 2
done

# --- токен администратора ---------------------------------------------------
TOKEN=$(curl -sf -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" -d "password=$ADMIN_PASSWORD" | jq -r '.access_token')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  log "не удалось получить токен администратора"
  exit 1
fi

# api METHOD PATH [BODY] — ответ складывает в $BODY, код возвращает в stdout
api() {
  method="$1"
  path="$2"
  payload="${3:-}"
  if [ -n "$payload" ]; then
    curl -s -o "$BODY" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d "$payload" "$KC_URL/admin/realms/$REALM$path"
  else
    curl -s -o "$BODY" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/$REALM$path"
  fi
}

get() { # get PATH -> тело ответа
  api GET "$1" >/dev/null
  cat "$BODY"
}

# --- 1. LDAP user federation ------------------------------------------------
LDAP_ID=$(get "/components?type=org.keycloak.storage.UserStorageProvider" \
  | jq -r '.[]? | select(.providerId=="ldap") | .id' | head -1)

if [ -z "$LDAP_ID" ]; then
  log "создаю LDAP-федерацию $LDAP_URL"
  api POST "/components" "$(jq -nc \
    --arg url "$LDAP_URL" --arg bind "$LDAP_BIND_DN" --arg pass "$LDAP_BIND_PASSWORD" \
    --arg users "$LDAP_USERS_DN" '{
      name: "bionicpro-ldap",
      providerId: "ldap",
      providerType: "org.keycloak.storage.UserStorageProvider",
      parentId: "reports-realm",
      config: {
        enabled: ["true"],
        priority: ["0"],
        vendor: ["other"],
        connectionUrl: [$url],
        bindDn: [$bind],
        bindCredential: [$pass],
        authType: ["simple"],
        usersDn: [$users],
        usernameLDAPAttribute: ["uid"],
        rdnLDAPAttribute: ["uid"],
        uuidLDAPAttribute: ["entryUUID"],
        userObjectClasses: ["inetOrgPerson"],
        searchScope: ["1"],
        editMode: ["READ_ONLY"],
        importEnabled: ["true"],
        syncRegistrations: ["false"],
        pagination: ["true"],
        batchSizeForSync: ["1000"],
        trustEmail: ["true"],
        validatePasswordPolicy: ["false"],
        allowKerberosAuthentication: ["false"],
        useTruststoreSpi: ["ldapsOnly"]
      }
    }')" >/dev/null
  LDAP_ID=$(get "/components?type=org.keycloak.storage.UserStorageProvider" \
    | jq -r '.[]? | select(.providerId=="ldap") | .id' | head -1)
fi
log "LDAP-федерация: $LDAP_ID"

# --- 2. мапперы LDAP --------------------------------------------------------
# ensure_mapper NAME JSON — создаёт маппер, если его ещё нет
ensure_mapper() {
  name="$1"
  json="$2"
  existing=$(get "/components?parent=$LDAP_ID&type=org.keycloak.storage.ldap.mappers.LDAPStorageMapper" \
    | jq -r --arg n "$name" '.[]? | select(.name==$n) | .id' | head -1)
  if [ -z "$existing" ]; then
    log "создаю LDAP-маппер $name"
    api POST "/components" "$json" >/dev/null
  else
    log "LDAP-маппер $name уже есть"
  fi
}

user_attr_mapper() { # user_attr_mapper NAME USER_ATTR LDAP_ATTR
  jq -nc --arg parent "$LDAP_ID" --arg n "$1" --arg ua "$2" --arg la "$3" '{
    name: $n,
    providerId: "user-attribute-ldap-mapper",
    providerType: "org.keycloak.storage.ldap.mappers.LDAPStorageMapper",
    parentId: $parent,
    config: {
      "user.model.attribute": [$ua],
      "ldap.attribute": [$la],
      "read.only": ["true"],
      "always.read.value.from.ldap": ["false"],
      "is.mandatory.in.ldap": ["false"]
    }
  }'
}

ensure_mapper "email" "$(user_attr_mapper email email mail)"
ensure_mapper "firstName" "$(user_attr_mapper firstName firstName cn)"
ensure_mapper "lastName" "$(user_attr_mapper lastName lastName sn)"

# роли представительства: LDAP-группы -> роли realm
ensure_mapper "realm-roles" "$(jq -nc --arg parent "$LDAP_ID" --arg groups "$LDAP_GROUPS_DN" '{
  name: "realm-roles",
  providerId: "role-ldap-mapper",
  providerType: "org.keycloak.storage.ldap.mappers.LDAPStorageMapper",
  parentId: $parent,
  config: {
    "roles.dn": [$groups],
    "realm.roles.dn": [$groups],
    "role.name.ldap.attribute": ["cn"],
    "role.object.classes": ["groupOfNames"],
    "membership.ldap.attribute": ["member"],
    "membership.attribute.type": ["DN"],
    "membership.user.ldap.attribute": ["uid"],
    "mode": ["READ_ONLY"],
    "user.roles.retrieve.strategy": ["LOAD_ROLES_BY_MEMBER_ATTRIBUTE"],
    "use.realm.roles.mapping": ["true"]
  }
}')"

# --- 3. обязательный OTP ----------------------------------------------------
# В стандартном flow `browser` шаг «Browser - Conditional OTP» переводим в REQUIRED:
# тогда OTP спрашивается у всех, кто его настроил, а остальным Keycloak
# выставляет required action CONFIGURE_TOTP.
FLOW=$(get "/authentication/flows/browser/executions")
OTP_EXEC=$(echo "$FLOW" | jq -r '[.[] | select(.displayName=="Browser - Conditional OTP")][0].id // empty')
if [ -n "$OTP_EXEC" ]; then
  CURRENT=$(echo "$FLOW" | jq -r '[.[] | select(.displayName=="Browser - Conditional OTP")][0].requirement')
  if [ "$CURRENT" != "REQUIRED" ]; then
    log "перевожу Browser - Conditional OTP в REQUIRED"
    api PUT "/authentication/flows/browser/executions" \
      "$(jq -nc --arg id "$OTP_EXEC" '{id:$id,requirement:"REQUIRED"}')" >/dev/null
  else
    log "OTP уже обязателен"
  fi
else
  log "ВНИМАНИЕ: не найден шаг Browser - Conditional OTP"
fi

# --- 4. права сервисной учётной записи bionicpro-auth ------------------------
AUTH_CLIENT=$(get "/clients?clientId=bionicpro-auth" | jq -r '.[0].id // empty')
if [ -n "$AUTH_CLIENT" ]; then
  SA_ID=$(get "/clients/$AUTH_CLIENT/service-account-user" | jq -r '.id // empty')
  RM_CLIENT=$(get "/clients?clientId=realm-management" | jq -r '.[0].id // empty')
  if [ -n "$SA_ID" ] && [ -n "$RM_CLIENT" ]; then
    ROLES=$(get "/clients/$RM_CLIENT/roles" \
      | jq -c '[.[] | select(.name=="view-users" or .name=="query-users" or .name=="view-realm") | {id:.id,name:.name}]')
    log "выдаю сервисной учётной записи права на чтение пользователей"
    api POST "/users/$SA_ID/role-mappings/clients/$RM_CLIENT" "$ROLES" >/dev/null || true
  fi
fi

# --- 5. ключи Яндекс ID из окружения ----------------------------------------
if [ -n "${YANDEX_CLIENT_ID:-}" ] && [ "${YANDEX_CLIENT_ID}" != "bionicpro-yandex-client-id" ]; then
  log "обновляю ключи Identity Provider «Яндекс ID»"
  UPDATED=$(get "/identity-provider/instances/yandex" \
    | jq -c --arg id "$YANDEX_CLIENT_ID" --arg secret "${YANDEX_CLIENT_SECRET:-}" \
      '.config.clientId=$id | .config.clientSecret=$secret')
  api PUT "/identity-provider/instances/yandex" "$UPDATED" >/dev/null || true
fi

# --- 6. синхронизация LDAP и обязательная настройка OTP ---------------------
log "синхронизирую пользователей из LDAP"
api POST "/user-storage/$LDAP_ID/sync?action=triggerFullSync" >/dev/null || true
sleep 3

log "выставляю required action CONFIGURE_TOTP пользователям без OTP"
get "/users?max=200" \
  | jq -r '.[] | select((.requiredActions // []) | index("CONFIGURE_TOTP") | not) | .id' \
  | while read -r uid; do
      [ -z "$uid" ] && continue
      api PUT "/users/$uid" "$(jq -nc '{requiredActions:["CONFIGURE_TOTP"]}')" >/dev/null || true
    done

log "готово"
