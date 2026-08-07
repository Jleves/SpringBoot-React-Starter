package com.ashenox.starter.security.model;

import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {

    @Test
    void mapsPersistenceUserToSecurityPrincipal() {
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("encoded")
                .role(Role.ADMIN)
                .enabled(false)
                .build();

        AuthenticatedUser principal = AuthenticatedUser.from(user);

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getPassword()).isEqualTo("encoded");
        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }
}
