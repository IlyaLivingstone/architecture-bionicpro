package ru.bionicpro.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Доступ к отчётам только для аутентифицированного пользователя.
 * Личность берётся из серверной сессии, поэтому подставить чужой идентификатор
 * в запросе невозможно.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = "bionicpro.user";

    private final SessionClient sessionClient;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(SessionClient sessionClient, ObjectMapper objectMapper) {
        this.sessionClient = sessionClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String cookie = request.getHeader("Cookie");
        SessionUser user = sessionClient.resolve(cookie)
                .filter(SessionUser::authenticated)
                .orElse(null);
        if (user == null || user.username() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("error", "требуется вход в систему")));
            return;
        }
        request.setAttribute(SESSION_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/reports");
    }
}
