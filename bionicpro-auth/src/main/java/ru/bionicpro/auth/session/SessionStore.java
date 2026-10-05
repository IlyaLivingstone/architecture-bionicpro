package ru.bionicpro.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.bionicpro.auth.config.AuthProperties;
import ru.bionicpro.auth.crypto.TokenCipher;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище серверных сессий в Redis.
 * Access- и refresh-токены лежат в распределённом кеше в зашифрованном виде
 * и привязаны к идентификатору сессии.
 */
@Component
public class SessionStore {

    private static final String KEY_PREFIX = "bionicpro:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TokenCipher cipher;
    private final Duration ttl;

    public SessionStore(StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        TokenCipher cipher,
                        AuthProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cipher = cipher;
        this.ttl = Duration.ofSeconds(properties.getSession().getTtlSeconds());
    }

    public SessionData create(SessionData data) {
        data.setId(UUID.randomUUID().toString());
        data.setCreatedAt(Instant.now());
        save(data);
        return data;
    }

    public void save(SessionData data) {
        try {
            StoredSession stored = new StoredSession();
            stored.userId = data.getUserId();
            stored.username = data.getUsername();
            stored.email = data.getEmail();
            stored.identityProvider = data.getIdentityProvider();
            stored.accessToken = cipher.encrypt(data.getAccessToken());
            stored.refreshToken = cipher.encrypt(data.getRefreshToken());
            stored.accessTokenExpiresAt = data.getAccessTokenExpiresAt();
            stored.createdAt = data.getCreatedAt();

            redis.opsForValue().set(KEY_PREFIX + data.getId(),
                    objectMapper.writeValueAsString(stored), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("не удалось сохранить сессию", e);
        }
    }

    public Optional<SessionData> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            StoredSession stored = objectMapper.readValue(json, StoredSession.class);
            SessionData data = new SessionData();
            data.setId(sessionId);
            data.setUserId(stored.userId);
            data.setUsername(stored.username);
            data.setEmail(stored.email);
            data.setIdentityProvider(stored.identityProvider);
            data.setAccessToken(cipher.decrypt(stored.accessToken));
            data.setRefreshToken(cipher.decrypt(stored.refreshToken));
            data.setAccessTokenExpiresAt(stored.accessTokenExpiresAt);
            data.setCreatedAt(stored.createdAt);
            return Optional.of(data);
        } catch (Exception e) {
            throw new IllegalStateException("не удалось прочитать сессию", e);
        }
    }

    /**
     * Ротация сессии: данные переносятся на новый идентификатор, старый удаляется.
     * Защищает от session fixation — идентификатор меняется на каждом запросе к защищённому ресурсу.
     */
    public SessionData rotate(SessionData current) {
        redis.delete(KEY_PREFIX + current.getId());
        current.setId(UUID.randomUUID().toString());
        save(current);
        return current;
    }

    public void delete(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redis.delete(KEY_PREFIX + sessionId);
        }
    }

    /** Представление сессии в Redis: токены хранятся зашифрованными. */
    public static class StoredSession {
        public String userId;
        public String username;
        public String email;
        public String identityProvider;
        public String accessToken;
        public String refreshToken;
        public Instant accessTokenExpiresAt;
        public Instant createdAt;
    }
}
