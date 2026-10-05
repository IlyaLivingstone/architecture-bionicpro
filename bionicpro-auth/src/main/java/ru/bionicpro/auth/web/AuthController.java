package ru.bionicpro.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.bionicpro.auth.config.AuthProperties;
import ru.bionicpro.auth.oauth.JwtClaims;
import ru.bionicpro.auth.oauth.KeycloakClient;
import ru.bionicpro.auth.oauth.LoginStateStore;
import ru.bionicpro.auth.oauth.Pkce;
import ru.bionicpro.auth.profile.ProfileService;
import ru.bionicpro.auth.session.SessionData;
import ru.bionicpro.auth.session.SessionManager;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Вход, возврат от Keycloak и управление серверной сессией.
 * PKCE выполняется на стороне сервиса: браузер не получает ни кода, ни токенов.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthProperties properties;
    private final KeycloakClient keycloak;
    private final LoginStateStore loginStateStore;
    private final SessionManager sessionManager;
    private final SessionCookieService cookieService;
    private final ProfileService profileService;

    public AuthController(AuthProperties properties,
                          KeycloakClient keycloak,
                          LoginStateStore loginStateStore,
                          SessionManager sessionManager,
                          SessionCookieService cookieService,
                          ProfileService profileService) {
        this.properties = properties;
        this.keycloak = keycloak;
        this.loginStateStore = loginStateStore;
        this.sessionManager = sessionManager;
        this.cookieService = cookieService;
        this.profileService = profileService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletRequest request,
                                      @RequestParam(required = false) String returnTo) {
        String state = Pkce.randomState();
        String codeVerifier = Pkce.generateCodeVerifier();
        String redirectUri = baseUrl(request) + "/auth/callback";

        LoginStateStore.LoginState loginState = new LoginStateStore.LoginState();
        loginState.codeVerifier = codeVerifier;
        loginState.redirectUri = redirectUri;
        loginState.returnTo = safeReturnTo(returnTo);
        loginStateStore.save(state, loginState);

        String authorizationUrl = keycloak.authorizationUrl(state, Pkce.codeChallenge(codeVerifier), redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
                                         @RequestParam("state") String state) {
        LoginStateStore.LoginState loginState = loginStateStore.consume(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "неизвестное состояние входа"));

        KeycloakClient.TokenResponse tokens = keycloak.exchangeCode(
                code, loginState.codeVerifier, loginState.redirectUri);

        Map<String, Object> idClaims = JwtClaims.parse(tokens.idToken());
        String userId = JwtClaims.stringClaim(idClaims, "sub");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak не вернул идентификатор пользователя");
        }

        String identityProvider = profileService.resolveIdentityProvider(userId);
        profileService.sync(userId,
                JwtClaims.stringClaim(idClaims, "preferred_username"),
                JwtClaims.stringClaim(idClaims, "email"),
                JwtClaims.stringClaim(idClaims, "given_name"),
                JwtClaims.stringClaim(idClaims, "family_name"),
                identityProvider);

        SessionData session = new SessionData();
        session.setUserId(userId);
        session.setUsername(JwtClaims.stringClaim(idClaims, "preferred_username"));
        session.setEmail(JwtClaims.stringClaim(idClaims, "email"));
        session.setIdentityProvider(identityProvider);
        session.setAccessToken(tokens.accessToken());
        session.setRefreshToken(tokens.refreshToken());
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
        session = sessionManager.create(session);

        ResponseCookie cookie = cookieService.create(session.getId());
        String target = loginState.returnTo != null ? loginState.returnTo : properties.getFrontendUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(target))
                .build();
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        SessionData session = currentSession(request);
        Map<String, Object> claims = JwtClaims.parse(session.getAccessToken());
        return new SessionResponse(true,
                session.getUserId(),
                session.getUsername(),
                session.getEmail(),
                session.getIdentityProvider(),
                JwtClaims.realmRoles(claims),
                session.getAccessTokenExpiresAt());
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(HttpServletRequest request) {
        SessionData session = sessionManager.refresh(currentSession(request));
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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SessionData session = currentSession(request);
        sessionManager.destroy(session);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .build();
    }

    private SessionData currentSession(HttpServletRequest request) {
        Object attribute = request.getAttribute("bionicpro.session");
        if (attribute instanceof SessionData session) {
            return session;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "сессия не найдена");
    }

    /** Защита от открытого редиректа: возвращаем только на адрес веб-приложения. */
    private String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return null;
        }
        return returnTo.startsWith(properties.getFrontendUrl()) ? returnTo : null;
    }

    private String baseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }
}
