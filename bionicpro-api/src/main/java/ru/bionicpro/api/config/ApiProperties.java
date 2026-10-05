package ru.bionicpro.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bionicpro.api")
public class ApiProperties {

    private String frontendUrl = "http://localhost:3000";

    private final Auth auth = new Auth();
    private final ClickHouse clickhouse = new ClickHouse();
    private final Storage storage = new Storage();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public Auth getAuth() {
        return auth;
    }

    public ClickHouse getClickhouse() {
        return clickhouse;
    }

    public Storage getStorage() {
        return storage;
    }

    /** Объектное хранилище отчётов (S3 API) и CDN перед ним. */
    public static class Storage {

        private String endpoint = "http://minio:9000";
        private String accessKey = "minio_user";
        private String secretKey = "minio_password";
        private String bucket = "bionicpro-reports";

        /** Адрес CDN, который раздаёт отчёты браузеру. */
        private String cdnUrl = "http://localhost:8082";

        /** Срок жизни ссылки на отчёт: больше времени жизни записи в кеше CDN. */
        private int presignTtlSeconds = 3600;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getCdnUrl() {
            return cdnUrl;
        }

        public void setCdnUrl(String cdnUrl) {
            this.cdnUrl = cdnUrl;
        }

        public int getPresignTtlSeconds() {
            return presignTtlSeconds;
        }

        public void setPresignTtlSeconds(int presignTtlSeconds) {
            this.presignTtlSeconds = presignTtlSeconds;
        }
    }

    public static class Auth {

        private String baseUrl = "http://bionicpro-auth:8081";

        /** Общий секрет: эндпоинт проверки сессии доступен только внутренним сервисам. */
        private String internalToken = "bionicpro-internal-token";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalToken() {
            return internalToken;
        }

        public void setInternalToken(String internalToken) {
            this.internalToken = internalToken;
        }
    }

    public static class ClickHouse {

        /** HTTP-интерфейс ClickHouse. */
        private String url = "http://clickhouse:8123";

        private String database = "reports";
        private String user = "default";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
