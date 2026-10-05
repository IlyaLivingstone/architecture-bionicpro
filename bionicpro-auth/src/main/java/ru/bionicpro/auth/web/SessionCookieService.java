package ru.bionicpro.auth.web;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import ru.bionicpro.auth.config.AuthProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Сессионная cookie: единственное, что сервис отдаёт в браузер.
 * HttpOnly защищает от чтения из JavaScript, SameSite=Lax — от межсайтовых запросов.
 */
@Component
public class SessionCookieService {

    private final AuthProperties properties;

    public SessionCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public String cookieName() {
        return properties.getSession().getCookieName();
    }

    public ResponseCookie create(String sessionId) {
        return ResponseCookie.from(cookieName(), sessionId)
                .httpOnly(true)
                .secure(properties.getSession().isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(properties.getSession().getTtlSeconds()))
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(cookieName(), "")
                .httpOnly(true)
                .secure(properties.getSession().isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
