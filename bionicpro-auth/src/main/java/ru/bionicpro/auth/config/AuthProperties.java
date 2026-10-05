package ru.bionicpro.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки сервиса аутентификации. Значения приходят из переменных окружения
 * (см. docker-compose.yaml), в коде остаются только значения по умолчанию для локального запуска.
 */
@ConfigurationProperties(prefix = "bionicpro.auth")
public class AuthProperties {

    /** Адрес веб-приложения, куда возвращаем пользователя после входа. */
    private String frontendUrl = "http://localhost:3000";

    /** Ключ AES-256 (base64) для шифрования refresh-токенов перед записью в Redis. */
    private String tokenEncryptionKey;

    /** Общий секрет для внутренних сервисов (проверка сессии без участия пользователя). */
    private String internalToken = "bionicpro-internal-token";

    private final Keycloak keycloak = new Keycloak();
    private final Session session = new Session();
    private final Yandex yandex = new Yandex();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public Session getSession() {
        return session;
    }

    public Yandex getYandex() {
        return yandex;
    }

    public static class Keycloak {

        /** Внутренний адрес Keycloak (обращения сервиса к IdP). */
        private String baseUrl = "http://localhost:8080";

        /** Публичный адрес Keycloak (на него уходит браузер). */
        private String publicUrl = "http://localhost:8080";

        private String realm = "reports-realm";
        private String clientId = "reports-frontend";
        private String clientSecret = "";

        /** Сервисная учётная запись для чтения связанных учётных записей IdP. */
        private String adminClientId = "bionicpro-auth";
        private String adminClientSecret = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAdminClientId() {
            return adminClientId;
        }

        public void setAdminClientId(String adminClientId) {
            this.adminClientId = adminClientId;
        }

        public String getAdminClientSecret() {
            return adminClientSecret;
        }

        public void setAdminClientSecret(String adminClientSecret) {
            this.adminClientSecret = adminClientSecret;
        }
    }

    public static class Session {

        /** Время жизни серверной сессии; должно быть больше времени жизни access-токена. */
        private long ttlSeconds = 1800;

        private String cookieName = "BIONICPRO_SESSION";

        private boolean cookieSecure = true;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Yandex {

        /** Метод Яндекс ID, возвращающий профиль пользователя по токену. */
        private String profileUrl = "https://login.yandex.ru/info?format=json";

        public String getProfileUrl() {
            return profileUrl;
        }

        public void setProfileUrl(String profileUrl) {
            this.profileUrl = profileUrl;
        }
    }
}
