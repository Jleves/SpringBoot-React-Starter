package com.ashenox.starter.auth.session.service;

import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private AuthSessionRepository repository;
    private AuthSessionService service;
    private SessionRevocationService revocationService;

    @BeforeEach
    void setUp() {
        repository = mock(AuthSessionRepository.class);
        revocationService = mock(SessionRevocationService.class);
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setRefreshExpiration(Duration.ofHours(3));
        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AuthSessionService(repository, properties, revocationService);
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

    @Test
    void rejectsExpiredSession() {
        AuthSession session = session("session-id", Instant.now().minusSeconds(1), hashOf("secret"));
        when(repository.findWithUserById("session-id")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.rotate("session-id.secret"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokesSessionWhenPreviousSecretIsReused() {
        AuthSession session = session("session-id", Instant.now().plusSeconds(60), hashOf("current-secret"));
        when(repository.findWithUserById("session-id")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.rotate("session-id.previous-secret"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(revocationService).revokeInNewTransaction("session-id");
    }

    @Test
    void revokesSessionAfterOptimisticConcurrencyConflict() {
        AuthSession session = session("session-id", Instant.now().plusSeconds(60), hashOf("secret"));
        when(repository.findWithUserById("session-id")).thenReturn(Optional.of(session));
        when(repository.saveAndFlush(session))
                .thenThrow(new OptimisticLockingFailureException("concurrent refresh"));

        assertThatThrownBy(() -> service.rotate("session-id.secret"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(revocationService).revokeInNewTransaction("session-id");
    }

    private AuthSession session(String id, Instant expiresAt, String refreshHash) {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setUser(User.builder().id(1L).email("user@example.com").role(Role.USER).enabled(true).build());
        session.setExpiresAt(expiresAt);
        session.setLastUsedAt(Instant.now());
        session.setRefreshTokenHash(refreshHash);
        return session;
    }

    private String hashOf(String secret) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
