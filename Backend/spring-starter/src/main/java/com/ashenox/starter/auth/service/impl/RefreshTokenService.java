package com.ashenox.starter.auth.service.impl;





import com.ashenox.starter.auth.model.RefreshToken;
import com.ashenox.starter.auth.repository.RefreshTokenRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {


    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    private AppProperties appProperties;

    @Autowired
    private UserRepository userRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AppProperties appProperties, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.appProperties = appProperties;
        this.userRepository = userRepository;
    }
@Transactional
    public RefreshToken createRefreshToken(String username, Long id) {

        User user = userRepository.findByEmail(username).orElseThrow();
        refreshTokenRepository.deleteByUser_Id(user.getId());
        Long expirationHours= appProperties.getSecurity().getJwt().getRefreshExpirationHours();


        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plus(expirationHours, ChronoUnit.HOURS));

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expirado.");
        }
        return token;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public void delete(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

   /* public boolean existsByUser(Long id){
       return refreshTokenRepository.existsByUserId(id);
    }*/
}
