package ru.bionicpro.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Параметры PKCE (RFC 7636): code_verifier и вычисленный из него code_challenge.
 */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    public static String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String codeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("не удалось вычислить code_challenge", e);
        }
    }

    public static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
