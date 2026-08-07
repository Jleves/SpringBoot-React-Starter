package com.ashenox.starter.user.bootstrap;

import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.SuperAdmin config = appProperties.getInit().getSuperadmin();
        if (!config.isEnabled()) {
            return;
        }

        String email = EmailNormalizer.normalize(config.getEmail());
        if (userRepository.existsByEmail(email)) {
            LOGGER.info("Bootstrap SUPER_ADMIN omitido: el usuario configurado ya existe");
            return;
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(config.getPassword()))
                .role(Role.SUPER_ADMIN)
                .enabled(true)
                .build();
        userRepository.save(user);
        LOGGER.info("Bootstrap SUPER_ADMIN completado");
    }
}
