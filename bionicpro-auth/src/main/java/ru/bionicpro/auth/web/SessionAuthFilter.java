package ru.bionicpro.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.bionicpro.auth.oauth.JwtClaims;
import ru.bionicpro.auth.session.SessionData;
import ru.bionicpro.auth.session.SessionManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Проверка сессии на защищённых запросах.
 * На каждом запросе сервис проверяет cookie, при необходимости обновляет access-токен
 * и выполняет ротацию сессии, чтобы исключить session fixation.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PATHS = List.of(
            "/auth/session",
            "/auth/logout",
            "/auth/refresh",
            "/api/"
    );

    private final SessionCookieService cookieService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(SessionCookieService cookieService,
                             SessionManager sessionManager,
                             ObjectMapper objectMapper) {
        this.cookieService = cookieService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PROTECTED_PATHS.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> sessionId = cookieService.read(request);
        if (sessionId.isEmpty()) {
            unauthorized(response, "сессия не найдена");
            return;
        }

        SessionData session;
        try {
            session = sessionManager.load(sessionId.get())
                    .orElseThrow(() -> new IllegalStateException("сессия не найдена или истекла"));
            session = sessionManager.refreshIfNeeded(session);
            session = sessionManager.rotate(session);
        } catch (Exception e) {
            unauthorized(response, "сессия недействительна");
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(session.getId()).toString());

        Map<String, Object> claims = JwtClaims.parse(session.getAccessToken());
        List<SimpleGrantedAuthority> authorities = JwtClaims.realmRoles(claims).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, null, authorities));
        request.setAttribute("bionicpro.session", session);

        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }
}
