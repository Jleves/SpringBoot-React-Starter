package com.ashenox.starter.user.service.impl;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.AdminUserService;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request, Role actorRole) {
        if (actorRole != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Solamente SUPER_ADMIN puede crear usuarios");
        }

        String email = EmailNormalizer.normalize(request.email());
        com.ashenox.starter.auth.passwordchange.PasswordUpdateService.validate(request.password());
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .build();
        return UserResponse.from(userRepository.saveAndFlush(user));
    }
}
