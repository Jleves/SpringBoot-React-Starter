package com.ashenox.starter.auth.passwordchange;

import com.ashenox.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordUpdateService passwords;

    // Persist failed attempts across instances; expected rejection must not roll them back.
    @Transactional(noRollbackFor = PasswordChangeException.class)
    public void change(Long userId, ChangePasswordRequest request) {
        var user = users.lockById(userId).orElseThrow(() -> new AccessDeniedException("Cuenta no disponible"));
        if (!user.isEnabled()) throw new AccessDeniedException("Cuenta no disponible");
        Instant now = Instant.now();
        if (user.getPasswordChangeWindowStart() == null
                || !now.isBefore(user.getPasswordChangeWindowStart().plusSeconds(900))) {
            user.setPasswordChangeWindowStart(now);
            user.setPasswordChangeFailures(0);
        }
        if (user.getPasswordChangeFailures() >= 5) {
            long remaining = Math.max(1, user.getPasswordChangeWindowStart().plusSeconds(900).getEpochSecond() - now.getEpochSecond());
            throw new PasswordChangeException("currentPassword", "Demasiados intentos. Volvé a intentar más tarde.", remaining);
        }
        if (request.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            user.setPasswordChangeFailures(user.getPasswordChangeFailures() + 1);
            throw new PasswordChangeException("currentPassword", "La contraseña actual es incorrecta.", 0);
        }
        try {
            PasswordUpdateService.validate(request.newPassword());
        } catch (com.ashenox.starter.shared.error.BusinessException exception) {
            throw new PasswordChangeException("newPassword", exception.getMessage(), 0);
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new PasswordChangeException("newPassword", "La contraseña nueva debe ser diferente de la actual.", 0);
        }
        passwords.update(user, request.newPassword());
    }
}
