package ru.bionicpro.api.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.bionicpro.api.config.ApiProperties;

import java.util.Optional;

/**
 * Проверка сессии в сервисе аутентификации.
 * Токены удостоверяющей службы в сервис отчётов не передаются: он получает
 * только идентификатор пользователя и его роли по серверной сессии.
 */
@Component
public class SessionClient {

    private final RestClient http;
    private final ApiProperties properties;

    public SessionClient(RestClient.Builder builder, ApiProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    public Optional<SessionUser> resolve(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Optional.empty();
        }
        try {
            SessionUser user = http.get()
                    .uri(properties.getAuth().getBaseUrl() + "/internal/session")
                    .header("Cookie", cookieHeader)
                    .header("X-Internal-Token", properties.getAuth().getInternalToken())
                    .retrieve()
                    .body(SessionUser.class);
            return Optional.ofNullable(user);
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("сервис аутентификации недоступен: " + e.getMessage(), e);
        }
    }
}
