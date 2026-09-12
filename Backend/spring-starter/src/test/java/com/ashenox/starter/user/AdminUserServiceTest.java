package com.ashenox.starter.user;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminUserServiceTest {
    @Test
    void rejectsNonSuperAdminEvenWhenCalledWithoutHttpSecurity() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(repository, encoder);
        for (Role actor : new Role[]{Role.ADMIN, Role.USER, null}) {
            for (Role target : Role.values()) {
                assertThatThrownBy(() -> service.create(
                        new CreateUserRequest("new@example.com", "initial-password", target), actor))
                        .isInstanceOf(AccessDeniedException.class);
            }
        }
        verifyNoInteractions(repository, encoder);
    }
}
