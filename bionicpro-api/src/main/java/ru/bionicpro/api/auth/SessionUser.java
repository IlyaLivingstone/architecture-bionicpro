package ru.bionicpro.api.auth;

import java.util.List;

public record SessionUser(boolean authenticated,
                          String userId,
                          String username,
                          String email,
                          String identityProvider,
                          List<String> roles) {
}
