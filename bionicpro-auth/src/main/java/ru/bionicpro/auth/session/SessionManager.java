package ru.bionicpro.auth.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.bionicpro.auth.oauth.KeycloakClient;

import java.time.Instant;
import java.util.Optional;

/**
 * Работа с серверной сессией: обновление access-токена по refresh-токену,
 * ротация идентификатора сессии и завершение сессии.
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;
    private final KeycloakClient keycloak;

    public SessionManager(SessionStore store, KeycloakClient keycloak) {
        this.store = store;
        this.keycloak = keycloak;
    }

    public Optional<SessionData> load(String sessionId) {
        return store.find(sessionId);
    }

    /** Создание новой серверной сессии после успешного входа. */
    public SessionData create(SessionData session) {
        return store.create(session);
    }

    /**
     * Если access-токен истёк, сервис сам обновляет его в Keycloak по refresh-токену.
     * Если обновить не удалось — сессия считается недействительной.
     */
    public SessionData refreshIfNeeded(SessionData session) {
        if (!session.isAccessTokenExpired()) {
            return session;
        }
        if (session.getRefreshToken() == null) {
            throw new IllegalStateException("refresh-токен отсутствует");
        }
        KeycloakClient.TokenResponse tokens = keycloak.refresh(session.getRefreshToken());
        session.setAccessToken(tokens.accessToken());
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
        if (tokens.refreshToken() != null) {
            session.setRefreshToken(tokens.refreshToken());
        }
        log.debug("access-токен пользователя {} обновлён", session.getUsername());
        return session;
    }

    public SessionData refresh(SessionData session) {
        session.setAccessTokenExpiresAt(Instant.EPOCH);
        return refreshIfNeeded(session);
    }

    /** Ротация идентификатора сессии: токены привязываются к новой сессии. */
    public SessionData rotate(SessionData session) {
        return store.rotate(session);
    }

    public void save(SessionData session) {
        store.save(session);
    }

    public void destroy(SessionData session) {
        keycloak.revoke(session.getRefreshToken());
        store.delete(session.getId());
    }
}
