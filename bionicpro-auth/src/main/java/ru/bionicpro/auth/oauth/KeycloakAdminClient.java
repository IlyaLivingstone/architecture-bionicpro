package ru.bionicpro.auth.oauth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.bionicpro.auth.config.AuthProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Обращения к Admin REST API Keycloak от имени сервисной учётной записи bionicpro-auth.
 * Нужны, чтобы узнать, через какого внешнего провайдера вошёл пользователь,
 * и получить токен брокера для запроса профиля у внешней службы.
 */
@Component
public class KeycloakAdminClient {

    private final RestClient http;
    private final AuthProperties properties;

    private volatile String serviceToken;
    private volatile Instant serviceTokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> federatedIdentities(String userId) {
        try {
            List<Map<String, Object>> result = http.get()
                    .uri(adminUrl("/users/{id}/federated-identity"), userId)
                    .header("Authorization", "Bearer " + serviceToken())
                    .retrieve()
                    .body(List.class);
            return result == null ? List.of() : result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Токен доступа, выданный внешней службе (например, Яндекс ID) при входе пользователя. */
    @SuppressWarnings("unchecked")
    public String brokerAccessToken(String userId, String provider) {
        try {
            Map<String, Object> body = http.get()
                    .uri(adminUrl("/users/{id}/federated-identity/{provider}/token"), userId, provider)
                    .header("Authorization", "Bearer " + serviceToken())
                    .retrieve()
                    .body(Map.class);
            return body == null ? null : (String) body.get("access_token");
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized String serviceToken() {
        if (serviceToken != null && Instant.now().isBefore(serviceTokenExpiresAt)) {
            return serviceToken;
        }
        String url = properties.getKeycloak().getBaseUrl()
                + "/realms/" + properties.getKeycloak().getRealm()
                + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getKeycloak().getAdminClientId());
        form.add("client_secret", properties.getKeycloak().getAdminClientSecret());

        KeycloakClient.TokenPayload payload = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakClient.TokenPayload.class);

        if (payload == null || payload.access_token == null) {
            throw new IllegalStateException("Keycloak не выдал токен сервисной учётной записи");
        }
        serviceToken = payload.access_token;
        serviceTokenExpiresAt = Instant.now().plusSeconds(Math.max(5, payload.expires_in - 30));
        return serviceToken;
    }

    private String adminUrl(String path) {
        return properties.getKeycloak().getBaseUrl()
                + "/admin/realms/" + properties.getKeycloak().getRealm() + path;
    }
}
