# Задание 1. Повышение безопасности системы

Ниже — что сделано по каждой подзадаче и где смотреть код.
Код и конфигурации живут в дереве приложения (в корне репозитория) — это тот же
проект, который выдал Практикум, и в него же делается пул-реквест. В этой папке
собраны артефакты-результаты по заданию.

## Задача 1. Архитектура управления учётными данными

* `BionicPRO_C4_model_to-be.drawio`: поставляемая схема C4, дополненная контуром
  аутентификации (`.png` рядом).
* Добавлены: веб-приложение без токенов, сервис `bionicpro-auth`, хранилище сессий,
  база профилей, Keycloak, LDAP представительства, Яндекс ID.
* Решения: учётные записи читаются из внешнего источника в стране представительства;
  токены удостоверяющей службы в браузер не передаются; разные страны подключаются
  как отдельные удостоверяющие службы через Identity Brokering.

## Задача 2. PKCE вместо Code Grant

* `keycloak/realm-export.json`: клиент `reports-frontend`: атрибут
  `pkce.code.challenge.method = S256`, `redirectUris` указывает на сервис аутентификации.
* `bionicpro-auth/src/main/java/ru/bionicpro/auth/oauth/Pkce.java`: генерация
  `code_verifier` и вычисление `code_challenge` (SHA-256).
* `bionicpro-auth/src/main/java/ru/bionicpro/auth/web/AuthController.java` —
  `/auth/login` и `/auth/callback`: сохранение `code_verifier` и обмен кода на токены.
* `bionicpro-auth/src/main/java/ru/bionicpro/auth/oauth/LoginStateStore.java` —
  состояние входа в Redis (TTL 5 минут, одноразовое использование).

## Задача 3. Безопасные access- и refresh-токены

| Требование задания | Где реализовано |
| --- | --- |
| 1. Запрос токенов перенесён с фронтенда на бэкенд | сервис `bionicpro-auth/`, фронтенд больше не обращается к Keycloak |
| 2. Сервис называется `bionicpro-auth` | `bionicpro-auth/` |
| 3. Язык и библиотеки | Java 21 + Spring Boot 3: `bionicpro-auth/pom.xml` |
| 4. Keycloak работает с refresh_token, access_token ≤ 2 минут | `keycloak/realm-export.json`: `accessTokenLifespan: 120`, `revokeRefreshToken`, `use.refresh.tokens` |
| 5. refresh_token в защищённом хранилище в зашифрованном виде | `crypto/TokenCipher.java` (AES-256-GCM) + `session/SessionStore.java` (Redis) |
| 6. access_token в распределённом кеше | `session/SessionStore.java` |
| 7. Привязка токенов к сессии | `session/SessionData.java`, ключ `bionicpro:session:{id}` |
| 8. Фронтенду — сессионная cookie с флагами HTTP-only и Secure | `web/SessionCookieService.java` |
| 9. Время жизни сессии больше времени жизни access_token | `application.yml`: сессия 1800 секунд против 120 у токена |
| 10. Фронтенд обновлён: токенов нет, cookie обязательна | `frontend/src/auth/session.ts`, `frontend/src/App.tsx`, `frontend/src/components/ReportPage.tsx` |
| 11. Истёкший access_token обновляется по refresh_token | `session/SessionManager.java` |
| 12. Ротация сессии | `web/SessionAuthFilter.java` + `SessionStore.rotate` |

## Задача 4. LDAP представительства

* `ldap/bootstrap/50-bionicpro.ldif`: пользователи (`john.doe`, `jane.smith`,
  `alex.johnson`) и группы-роли (`user`, `prothetic_user`); каталог разворачивается
  сервисом `ldap` из `docker-compose.yaml`.
* `keycloak/setup/configure-realm.sh`: федерация `bionicpro-ldap` и маппер
  `realm-roles` (LDAP-группы → роли realm, `LOAD_ROLES_BY_MEMBER_ATTRIBUTE`).

## Задача 5. MFA

* `keycloak/setup/configure-realm.sh`: шаг «Browser — Conditional OTP» переводится
  в `REQUIRED`, всем пользователям выставляется действие `CONFIGURE_TOTP`.
* `keycloak/realm-export.json`: политика OTP (TOTP, 6 цифр, 30 секунд,
  поддерживаются FreeOTP и Google Authenticator).

## Задача 6. OAuth 2.0 от Яндекс ID

* `keycloak/realm-export.json`: Identity Provider `yandex` (OIDC, `storeToken`,
  PKCE) и мапперы атрибутов профиля.
* `bionicpro-auth/src/main/java/ru/bionicpro/auth/profile/ProfileService.java` —
  профиль запрашивается у Яндекса и сохраняется в таблицу `user_profile`.
* `.../profile/YandexProfileClient.java`: обращение к `login.yandex.ru/info`.
* `.../oauth/KeycloakAdminClient.java`: определение провайдера пользователя
  и получение токена брокера.
* `.../resources/schema.sql`: таблица `user_profile`.

## Как проверить

```bash
cp .env.example .env
docker compose up -d --build
sh keycloak/setup/export-realm.sh     # обновить keycloak/keycloak-results-export.json
```

Далее открыть <http://localhost:3000>, нажать Login и войти:

* `user1` / `password123` — локальная учётная запись realm, роль `user`;
* `john.doe` / `password` — пользователь LDAP представительства, роль `prothetic_user`.

При первом входе Keycloak попросит настроить одноразовый пароль, при следующих —
запросит код. В браузере остаётся только cookie `BIONICPRO_SESSION`; токенов
удостоверяющей службы в ответах и в хранилище браузера нет.
