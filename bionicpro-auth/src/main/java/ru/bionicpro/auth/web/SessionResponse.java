package ru.bionicpro.auth.web;

import java.time.Instant;
import java.util.List;

public record SessionResponse(boolean authenticated,
                              String userId,
                              String username,
                              String email,
                              String identityProvider,
                              List<String> roles,
                              Instant accessTokenExpiresAt) {
}
