package ru.bionicpro.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Разбор JWT, полученного от Keycloak по back channel.
 * Подпись проверять не нужно: токен пришёл напрямую от IdP по защищённому каналу,
 * наружу (в браузер) он не отдаётся.
 */
public final class JwtClaims {

    private JwtClaims() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return Collections.emptyMap();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return new ObjectMapper().readValue(new String(payload, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> realmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> access) {
            Object roles = access.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    public static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }
}
