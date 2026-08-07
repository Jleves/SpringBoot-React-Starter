package com.ashenox.starter.auth.passwordreset.repository;

import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByUser(User user);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
