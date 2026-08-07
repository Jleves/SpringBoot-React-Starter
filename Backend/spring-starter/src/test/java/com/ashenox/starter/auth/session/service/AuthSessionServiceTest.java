package com.ashenox.starter.auth.session.service;

import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private AuthSessionRepository repository;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthSessionRepository.class);
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setRefreshExpiration(Duration.ofHours(3));
        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AuthSessionService(repository, properties);
    }

    @Test
    void storesOnlyHashOfRefreshSecret() {
        User user = User.builder()
                .id(1L)
                .email("admin@example.com")
                .passwordHash("hash")
                .role(Role.SUPER_ADMIN)
                .enabled(true)
                .build();

        IssuedSession issued = service.createSession(user);
        String secret = issued.refreshToken().substring(issued.refreshToken().indexOf('.') + 1);

        assertThat(issued.refreshToken()).startsWith(issued.session().getId() + ".");
        assertThat(issued.session().getRefreshTokenHash())
                .hasSize(64)
                .doesNotContain(secret)
                .isNotEqualTo(issued.refreshToken());
    }
}
