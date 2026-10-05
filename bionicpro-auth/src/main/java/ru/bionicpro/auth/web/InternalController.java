package ru.bionicpro.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.bionicpro.auth.config.AuthProperties;
import ru.bionicpro.auth.oauth.JwtClaims;
import ru.bionicpro.auth.session.SessionData;
import ru.bionicpro.auth.session.SessionManager;

import java.util.Map;

/**
 * Служебные эндпоинты для других сервисов BionicPRO.
 * Доступны только с общим секретом: браузер сюда не обращается.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final AuthProperties properties;
    private final SessionManager sessionManager;
    private final SessionCookieService cookieService;

    public InternalController(AuthProperties properties,
                              SessionManager sessionManager,
                              SessionCookieService cookieService) {
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.cookieService = cookieService;
    }

    /**
     * Проверка серверной сессии для сервиса отчётов.
     * Идентификатор сессии меняется только на запросах самого пользователя.
     */
    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        String token = request.getHeader("X-Internal-Token");
        if (token == null || !token.equals(properties.getInternalToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "внутренний доступ запрещён");
        }
        String sessionId = cookieService.read(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "сессия не найдена"));
        SessionData session = sessionManager.load(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "сессия недействительна"));
        session = sessionManager.refreshIfNeeded(session);
        sessionManager.save(session);

        Map<String, Object> claims = JwtClaims.parse(session.getAccessToken());
        return new SessionResponse(true,
                session.getUserId(),
                session.getUsername(),
                session.getEmail(),
                session.getIdentityProvider(),
                JwtClaims.realmRoles(claims),
                session.getAccessTokenExpiresAt());
    }
}
