package com.ashenox.starter.auth.service.impl;





import com.ashenox.starter.Exception.JWT.InvalidTokenException;
import com.ashenox.starter.auth.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.service.PasswordResetService;
import com.ashenox.starter.email.service.Interface.EmailService;
import com.ashenox.starter.security.config.PasswordEncoder;
import com.ashenox.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void createPasswordResetToken(String email) {
        var user = userRepository.findByEmailIgnoreCase(email);

        if (user == null) {
            log.info("[INFO] Solicitud de recuperacion para email no registrado: {}", email);
            return;
        }

        log.debug("[SUCCESS] Usuario encontrado: {}", user.getEmail());

        String tokenPlano = generarTokenPlano();
        String tokenHash = hashToken(tokenPlano);

        PasswordResetToken resetToken = tokenRepository.findByUser(user)
                .map(existingToken -> {
                    existingToken.setToken(tokenHash);
                    existingToken.setExpiration(LocalDateTime.now().plusMinutes(50));
                    existingToken.setUsed(false);
                    return existingToken;
                })
                .orElseGet(() -> new PasswordResetToken(
                        null,
                        tokenHash,
                        user,
                        LocalDateTime.now().plusMinutes(30),
                        false
                ));

        tokenRepository.save(resetToken);

        log.info("[SUCCESS] Token de recuperación guardado/actualizado para usuario: {}", user.getEmail());

        emailService.sendPasswordResetEmail(user.getEmail(), user.getEmail(), tokenPlano);
    }

    @Override
    public boolean validateToken(String token) {
        String tokenHash = hashToken(token);


        var resetToken = tokenRepository.findByToken(tokenHash)
                .filter(t -> !t.isUsed() && t.getExpiration().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new InvalidTokenException("Token inválido o expirado"));
        log.info("[SUCCESS] token valido");

        return true;
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        String tokenHash = hashToken(token);

        var resetToken = tokenRepository.findByToken(tokenHash)
                .filter(t -> !t.isUsed() && t.getExpiration().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new InvalidTokenException("Token inválido o expirado"));

        var user = resetToken.getUser();
        user.setPassword(passwordEncoder.bCryptPasswordEncoder().encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        log.info("[SUCCESS] Usuario restableció su contraseña exitosamente :  {}", user.getEmail());
    }

    private String generarTokenPlano() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String tokenPlano) {
        return DigestUtils.sha256Hex(tokenPlano);
    }
}
