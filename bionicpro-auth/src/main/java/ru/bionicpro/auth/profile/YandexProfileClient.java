package ru.bionicpro.auth.profile;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.bionicpro.auth.config.AuthProperties;

import java.util.Map;

/**
 * Запрос профиля пользователя у Яндекс ID по токену, полученному при входе через Identity Brokering.
 */
@Component
public class YandexProfileClient {

    private final RestClient http;
    private final AuthProperties properties;

    public YandexProfileClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> profile = http.get()
                    .uri(properties.getYandex().getProfileUrl())
                    .header("Authorization", "OAuth " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return profile == null ? Map.of() : profile;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
