package com.ashenox.starter.user.bootstrap;

import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAdminBootstrapTest {

    @Test
    void createsNormalizedSuperAdminOnlyWhenItDoesNotExist() {
        AppProperties properties = new AppProperties();
        AppProperties.SuperAdmin config = properties.getInit().getSuperadmin();
        config.setEnabled(true);
        config.setEmail("  Admin@Example.COM ");
        config.setPassword("secure-password");

        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.existsByEmail("admin@example.com")).thenReturn(false, true);
        when(encoder.encode("secure-password")).thenReturn("encoded-password");

        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(properties, repository, encoder);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();
        bootstrap.run(arguments);
        bootstrap.run(arguments);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("admin@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(savedUser.isEnabled()).isTrue();
    }
}
