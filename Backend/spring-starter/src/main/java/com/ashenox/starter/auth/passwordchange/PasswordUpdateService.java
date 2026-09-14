package com.ashenox.starter.auth.passwordchange;

import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordUpdateService {
    private final PasswordEncoder encoder;
    private final PasswordResetTokenRepository tokens;
    private final AuthSessionService sessions;

    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.");
        }
    }

    // Caller must lock the user before proving ownership and invoking this operation.
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(User user, String password) {
        validate(password);
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordChangeFailures(0);
        user.setPasswordChangeWindowStart(null);
        tokens.findByUser(user).ifPresent(token -> token.setUsedAt(Instant.now()));
        sessions.revokeAllForUser(user.getId());
    }
}
