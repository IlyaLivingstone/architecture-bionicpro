package ru.bionicpro.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Состояние незавершённого входа: code_verifier для PKCE, адрес возврата и защита от CSRF (state).
 * Живёт в Redis 5 минут и удаляется при первом обращении (одноразовое использование).
 */
@Component
public class LoginStateStore {

    private static final String KEY_PREFIX = "bionicpro:login-state:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public LoginStateStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void save(String state, LoginState loginState) {
        try {
            redis.opsForValue().set(KEY_PREFIX + state, objectMapper.writeValueAsString(loginState), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("не удалось сохранить состояние входа", e);
        }
    }

    public Optional<LoginState> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        String key = KEY_PREFIX + state;
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        redis.delete(key);
        try {
            return Optional.of(objectMapper.readValue(json, LoginState.class));
        } catch (Exception e) {
            throw new IllegalStateException("не удалось прочитать состояние входа", e);
        }
    }

    /** Данные, которые нужно сохранить между редиректом в Keycloak и обратным вызовом. */
    public static class LoginState {
        public String codeVerifier;
        public String redirectUri;
        public String returnTo;
    }
}
