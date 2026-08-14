package com.ashenox.starter.auth.session.service;

import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.user.model.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private static final int SECRET_BYTES = 32;
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final AuthSessionRepository sessionRepository;
    private final AppProperties appProperties;
    private final SessionRevocationService revocationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedSession createSession(User user) {
        Instant now = Instant.now();
        String sessionId = UUID.randomUUID().toString();
        String secret = generateSecret();

        AuthSession session = new AuthSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setRefreshTokenHash(hash(secret));
        session.setExpiresAt(now.plus(appProperties.getSecurity().getJwt().getRefreshExpiration()));
        session.setLastUsedAt(now);
        return new IssuedSession(sessionRepository.save(session), encode(sessionId, secret));
    }

    @Transactional
    public IssuedSession rotate(String refreshToken) {
        TokenParts parts = parse(refreshToken);
        AuthSession session = sessionRepository.findWithUserById(parts.sessionId())
                .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();
        if (!session.isActiveAt(now)) {
            throw invalidRefreshToken();
        }
        if (!matches(parts.secret(), session.getRefreshTokenHash())) {
            SECURITY_LOG.warn("event=refresh_reuse_detected userId={} sessionId={}",
                    session.getUser().getId(), session.getId());
            revocationService.revokeInNewTransaction(session.getId());
            throw invalidRefreshToken();
        }

        String newSecret = generateSecret();
        session.setRefreshTokenHash(hash(newSecret));
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(appProperties.getSecurity().getJwt().getRefreshExpiration()));
        try {
            return new IssuedSession(sessionRepository.saveAndFlush(session), encode(session.getId(), newSecret));
        } catch (OptimisticLockingFailureException exception) {
            SECURITY_LOG.warn("event=refresh_concurrency_rejected userId={} sessionId={}",
                    session.getUser().getId(), session.getId());
            revocationService.revokeInNewTransaction(session.getId());
            throw invalidRefreshToken();
        }
    }

    @Transactional(readOnly = true)
    public void revoke(String refreshToken) {
        TokenParts parts;
        try {
            parts = parse(refreshToken);
        } catch (InvalidRefreshTokenException exception) {
            return;
        }
        sessionRepository.findWithUserById(parts.sessionId())
                .filter(session -> matches(parts.secret(), session.getRefreshTokenHash()))
                .ifPresent(session -> revocationService.revokeInNewTransaction(session.getId()));
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return sessionRepository.revokeAllByUserId(userId, Instant.now());
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String sessionId, String secret) {
        return sessionId + "." + secret;
    }

    private TokenParts parse(String token) {
        if (token == null || token.isBlank()) {
            throw invalidRefreshToken();
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw invalidRefreshToken();
        }
        return new TokenParts(token.substring(0, separator), token.substring(separator + 1));
    }

    private InvalidRefreshTokenException invalidRefreshToken() {
        return new InvalidRefreshTokenException("Refresh token inválido o expirado");
    }

    private boolean matches(String secret, String expectedHash) {
        return MessageDigest.isEqual(
                hash(secret).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private record TokenParts(String sessionId, String secret) {
    }
}
