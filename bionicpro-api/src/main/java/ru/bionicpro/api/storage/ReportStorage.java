package ru.bionicpro.api.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.bionicpro.api.config.ApiProperties;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Хранилище сформированных отчётов в объектном хранилище с S3 API (MinIO).
 * Готовый отчёт — статический файл, поэтому его можно раздавать через CDN,
 * не обращаясь к OLAP-базе повторно.
 */
@Component
public class ReportStorage {

    private static final Logger log = LoggerFactory.getLogger(ReportStorage.class);

    private final MinioClient client;
    private final ApiProperties properties;

    public ReportStorage(ApiProperties properties) {
        this.properties = properties;
        ApiProperties.Storage storage = properties.getStorage();
        this.client = MinioClient.builder()
                .endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }

    @PostConstruct
    void prepareBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
                log.info("создан бакет {}", bucket());
            }
        } catch (Exception e) {
            log.warn("не удалось подготовить бакет {}: {}", bucket(), e.getMessage());
        }
    }

    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket()).object(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Последний сформированный отчёт пользователя (без обращения к OLAP-базе). */
    public String findLatest(String login) {
        try {
            String latest = null;
            for (var result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket())
                    .prefix(login + "/")
                    .recursive(true)
                    .build())) {
                String object = result.get().objectName();
                if (latest == null || object.compareTo(latest) > 0) {
                    latest = object;
                }
            }
            return latest;
        } catch (Exception e) {
            throw new IllegalStateException("не удалось прочитать список отчётов: " + e.getMessage(), e);
        }
    }

    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("не удалось сохранить отчёт в S3: " + e.getMessage(), e);
        }
    }

    /**
     * Ссылка для браузера: подписанный запрос к S3, но с адресом CDN.
     * Nginx проксирует запрос в MinIO, проверяя подпись, и кеширует ответ.
     */
    public String presignedCdnUrl(String key) {
        try {
            String presigned = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket())
                    .object(key)
                    .expiry(properties.getStorage().getPresignTtlSeconds(), TimeUnit.SECONDS)
                    .build());
            String query = presigned.contains("?") ? presigned.substring(presigned.indexOf('?')) : "";
            return properties.getStorage().getCdnUrl() + "/reports/" + key + query;
        } catch (Exception e) {
            throw new IllegalStateException("не удалось подписать ссылку на отчёт: " + e.getMessage(), e);
        }
    }

    /** Удаляет прошлые версии отчётов пользователя: они уже не нужны и не должны кешироваться. */
    public void deleteOlderVersions(String login, String keepKey) {
        try {
            List<String> stale = new ArrayList<>();
            for (var result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket())
                    .prefix(login + "/")
                    .recursive(true)
                    .build())) {
                String object = result.get().objectName();
                if (!object.equals(keepKey)) {
                    stale.add(object);
                }
            }
            for (String object : stale) {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(object).build());
            }
            if (!stale.isEmpty()) {
                log.info("удалено прошлых версий отчёта {}: {}", login, stale.size());
            }
        } catch (Exception e) {
            log.warn("не удалось удалить прошлые версии отчётов {}: {}", login, e.getMessage());
        }
    }

    private String bucket() {
        return properties.getStorage().getBucket();
    }
}
