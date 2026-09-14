package com.ashenox.starter.auth.service.impl;

import com.ashenox.starter.security.error.InvalidTokenException;
import com.ashenox.starter.auth.passwordreset.event.PasswordResetRequested;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.service.PasswordResetService;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties appProperties;
    private final com.ashenox.starter.auth.passwordchange.PasswordUpdateService passwordUpdateService;
    private final jakarta.persistence.EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void createPasswordResetToken(String email) {
        userRepository.lockByEmail(EmailNormalizer.normalize(email)).ifPresent(user -> {
            String plainToken = generatePlainToken();
            PasswordResetToken resetToken = tokenRepository.findByUser(user)
                    .orElseGet(PasswordResetToken::new);
            resetToken.setUser(user);
            resetToken.setTokenHash(hashToken(plainToken));
            resetToken.setExpiresAt(Instant.now().plus(
                    appProperties.getSecurity().getPasswordReset().getTokenTtl()));
            resetToken.setUsedAt(null);
            tokenRepository.save(resetToken);
            eventPublisher.publishEvent(new PasswordResetRequested(
                    user.getId(), user.getEmail(), user.getEmail(), plainToken));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        findUsableToken(token);
        return true;
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = findUsableToken(token);
        var user = userRepository.lockById(resetToken.getUser().getId()).orElseThrow();
        entityManager.refresh(resetToken, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(Instant.now())
                || !resetToken.getTokenHash().equals(hashToken(token))) {
            throw new InvalidTokenException("Token inválido o expirado");
        }
        passwordUpdateService.update(user, newPassword);
    }

    private PasswordResetToken findUsableToken(String plainToken) {
        Instant now = Instant.now();
        return tokenRepository.findByTokenHash(hashToken(plainToken))
                .filter(token -> token.getUsedAt() == null && token.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new InvalidTokenException("Token inválido o expirado"));
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String plainToken) {
        return DigestUtils.sha256Hex(plainToken);
    }
}
