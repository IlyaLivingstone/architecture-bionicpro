package ru.bionicpro.auth.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bionicpro.auth.oauth.KeycloakAdminClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация профиля пользователя с данными удостоверяющей службы.
 * Для пользователей, вошедших через Яндекс ID, профиль запрашивается у Яндекса
 * и сохраняется в базе данных сервиса.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserProfileRepository repository;
    private final KeycloakAdminClient adminClient;
    private final YandexProfileClient yandexClient;

    public ProfileService(UserProfileRepository repository,
                          KeycloakAdminClient adminClient,
                          YandexProfileClient yandexClient) {
        this.repository = repository;
        this.adminClient = adminClient;
        this.yandexClient = yandexClient;
    }

    @Transactional
    public UserProfile sync(String userId, String username, String email,
                            String firstName, String lastName, String identityProvider) {
        UserProfile profile = repository.findById(userId).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setId(userId);
            created.setCreatedAt(Instant.now());
            return created;
        });

        profile.setUsername(username);
        profile.setEmail(email);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setIdentityProvider(identityProvider);
        profile.setUpdatedAt(Instant.now());

        if ("yandex".equals(identityProvider)) {
            enrichFromYandex(profile, userId);
        }
        return repository.save(profile);
    }

    /** Провайдер, через которого пользователь вошёл в систему (по данным Keycloak). */
    public String resolveIdentityProvider(String userId) {
        List<Map<String, Object>> identities = adminClient.federatedIdentities(userId);
        if (identities.isEmpty()) {
            return "local";
        }
        return String.valueOf(identities.get(0).get("identityProvider"));
    }

    private void enrichFromYandex(UserProfile profile, String userId) {
        String brokerToken = adminClient.brokerAccessToken(userId, "yandex");
        if (brokerToken == null) {
            log.warn("не удалось получить токен брокера Яндекс ID для пользователя {}", userId);
            return;
        }
        Map<String, Object> yandex = yandexClient.fetchProfile(brokerToken);
        if (yandex.isEmpty()) {
            log.warn("Яндекс ID не вернул профиль пользователя {}", userId);
            return;
        }
        profile.setYandexId(asString(yandex.get("id")));
        profile.setYandexLogin(asString(yandex.get("login")));
        if (yandex.get("default_email") != null) {
            profile.setEmail(asString(yandex.get("default_email")));
        }
        if (yandex.get("first_name") != null) {
            profile.setFirstName(asString(yandex.get("first_name")));
        }
        if (yandex.get("last_name") != null) {
            profile.setLastName(asString(yandex.get("last_name")));
        }
        Map<?, ?> phone = yandex.get("default_phone") instanceof Map<?, ?> map ? map : null;
        if (phone != null && phone.get("number") != null) {
            profile.setPhone(asString(phone.get("number")));
        }
        if (Boolean.FALSE.equals(yandex.get("is_avatar_empty")) && yandex.get("default_avatar_id") != null) {
            profile.setAvatarUrl("https://avatars.yandex.net/get-yapic/"
                    + yandex.get("default_avatar_id") + "/islands-200");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
