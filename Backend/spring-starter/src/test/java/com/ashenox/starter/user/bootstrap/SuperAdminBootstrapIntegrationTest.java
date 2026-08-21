package com.ashenox.starter.user.bootstrap;

import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SuperAdminBootstrapIntegrationTest {

    @Autowired private SuperAdminBootstrap bootstrap;
    @Autowired private AppProperties properties;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void persistsInitialSuperAdminAndDoesNotDuplicateIt() {
        AppProperties.SuperAdmin config = properties.getInit().getSuperadmin();
        config.setEnabled(true);
        config.setEmail("Bootstrap.Admin@Example.COM");
        config.setPassword("bootstrap-password");

        try {
            bootstrap.run(new DefaultApplicationArguments());
            bootstrap.run(new DefaultApplicationArguments());

            assertThat(userRepository.count()).isEqualTo(1);
            User persisted = userRepository.findByEmail("bootstrap.admin@example.com").orElseThrow();
            assertThat(persisted.getRole()).isEqualTo(Role.SUPER_ADMIN);
            assertThat(persisted.isEnabled()).isTrue();
            assertThat(passwordEncoder.matches("bootstrap-password", persisted.getPasswordHash())).isTrue();
        } finally {
            config.setEnabled(false);
            config.setEmail(null);
            config.setPassword(null);
        }
    }
}
