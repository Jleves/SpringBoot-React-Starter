package com.ashenox.starter.user;

import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserPersistenceIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void normalizesEmailBeforePersisting() {
        User user = User.builder()
                .email("  User@Example.COM ")
                .passwordHash("$2a$10$1234567890123456789012345678901234567890123456789012")
                .role(Role.USER)
                .enabled(true)
                .build();

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(userRepository.findByEmail("user@example.com")).contains(saved);
    }
}
