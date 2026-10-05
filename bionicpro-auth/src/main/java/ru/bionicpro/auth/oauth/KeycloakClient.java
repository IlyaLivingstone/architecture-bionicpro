package ru.bionicpro.auth.oauth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.bionicpro.auth.config.AuthProperties;

import java.util.Map;

/**
 * Обращения сервиса к Keycloak: discovery, обмен кода авторизации на токены,
 * обновление токенов, отзыв и получение профиля.
 * Все вызовы идут по back channel — браузер токенов не видит.
 */
@Component
public class KeycloakClient {

    private final RestClient http;
    private final AuthProperties properties;
    private volatile OidcEndpoints cachedEndpoints;

    public KeycloakClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    public OidcEndpoints endpoints() {
        OidcEndpoints current = cachedEndpoints;
        if (current == null) {
            synchronized (this) {
                if (cachedEndpoints == null) {
                    cachedEndpoints = loadEndpoints();
                }
                current = cachedEndpoints;
            }
        }
        return current;
    }

    private OidcEndpoints loadEndpoints() {
        String url = properties.getKeycloak().getBaseUrl()
                + "/realms/" + properties.getKeycloak().getRealm()
                + "/.well-known/openid-configuration";
        Map<?, ?> body = http.get().uri(url).retrieve().body(Map.class);
        if (body == null) {
            throw new IllegalStateException("Keycloak не вернул конфигурацию OIDC");
        }
        return new OidcEndpoints(
                String.valueOf(body.get("authorization_endpoint")),
                String.valueOf(body.get("token_endpoint")),
                String.valueOf(body.get("end_session_endpoint")),
                String.valueOf(body.get("userinfo_endpoint")));
    }

    /** Адрес страницы входа Keycloak с параметрами PKCE. */
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        String base = properties.getKeycloak().getPublicUrl()
                + "/realms/" + properties.getKeycloak().getRealm()
                + "/protocol/openid-connect/auth";
        return base
                + "?response_type=code"
                + "&client_id=" + url(properties.getKeycloak().getClientId())
                + "&redirect_uri=" + url(redirectUri)
                + "&scope=" + url("openid profile email")
                + "&state=" + url(state)
                + "&code_challenge=" + url(codeChallenge)
                + "&code_challenge_method=S256";
    }

    public TokenResponse exchangeCode(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", properties.getKeycloak().getClientId());
        form.add("code_verifier", codeVerifier);
        addClientSecret(form);
        return postToken(form);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.getKeycloak().getClientId());
        addClientSecret(form);
        return postToken(form);
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        try {
            String url = properties.getKeycloak().getBaseUrl()
                    + "/realms/" + properties.getKeycloak().getRealm()
                    + "/protocol/openid-connect/revoke";
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", refreshToken);
            form.add("token_type_hint", "refresh_token");
            form.add("client_id", properties.getKeycloak().getClientId());
            addClientSecret(form);
            http.post().uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // отзыв токена не должен ломать выход пользователя
            cachedEndpoints = null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> userinfo(String accessToken) {
        return http.get()
                .uri(endpoints().userinfoEndpoint())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
    }

    private void addClientSecret(MultiValueMap<String, String> form) {
        String secret = properties.getKeycloak().getClientSecret();
        if (secret != null && !secret.isBlank()) {
            form.add("client_secret", secret);
        }
    }

    private TokenResponse postToken(MultiValueMap<String, String> form) {
        try {
            TokenPayload payload = http.post()
                    .uri(endpoints().tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenPayload.class);
            if (payload == null) {
                throw new IllegalStateException("Keycloak не вернул токены");
            }
            return new TokenResponse(payload.access_token, payload.refresh_token,
                    payload.id_token, payload.expires_in, payload.scope, payload.token_type);
        } catch (Exception e) {
            cachedEndpoints = null;
            throw new IllegalStateException("не удалось получить токены в Keycloak: " + e.getMessage(), e);
        }
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public record OidcEndpoints(String authorizationEndpoint,
                                String tokenEndpoint,
                                String endSessionEndpoint,
                                String userinfoEndpoint) {
    }

    public record TokenResponse(String accessToken,
                                String refreshToken,
                                String idToken,
                                long expiresIn,
                                String scope,
                                String tokenType) {
    }

    /** Ответ token endpoint Keycloak. */
    public static class TokenPayload {
        public String access_token;
        public String refresh_token;
        public String id_token;
        public long expires_in;
        public String scope;
        public String token_type;
    }
}
