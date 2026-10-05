package ru.bionicpro.auth.session;

import java.time.Instant;

/**
 * Серверная сессия пользователя. Токены хранятся только здесь (в защищённом виде),
 * браузер получает лишь идентификатор сессии в cookie.
 */
public class SessionData {

    private String id;
    private String userId;
    private String username;
    private String email;
    private String identityProvider;
    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiresAt;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Токен доступа истёк или истекает в ближайшие 10 секунд. */
    public boolean isAccessTokenExpired() {
        return accessTokenExpiresAt == null
                || accessTokenExpiresAt.isBefore(Instant.now().plusSeconds(10));
    }
}
