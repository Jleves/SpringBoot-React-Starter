package com.ashenox.starter.auth;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetAtomicityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthSessionService authSessionService;

    private Long userId;
    @Autowired private com.ashenox.starter.auth.passwordchange.PasswordChangeService passwordChangeService;

    @Test
    void rollsBackAuthenticatedChangeWhenSessionRevocationFails() {
        doThrow(new IllegalStateException("simulated revocation failure"))
                .when(authSessionService).revokeAllForUser(anyLong());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> passwordChangeService.change(userId,
                new com.ashenox.starter.auth.passwordchange.ChangePasswordRequest("original-password", "changed-password")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(passwordEncoder.matches("original-password", userRepository.findById(userId).orElseThrow().getPasswordHash())).isTrue();
        assertThat(tokenRepository.findByTokenHash(DigestUtils.sha256Hex("atomic-token")).orElseThrow().getUsedAt()).isNull();
    }

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.saveAndFlush(User.builder()
                .email("atomic@example.com")
                .passwordHash(passwordEncoder.encode("original-password"))
                .role(Role.USER)
                .enabled(true)
                .build());
        userId = user.getId();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(DigestUtils.sha256Hex("atomic-token"));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        tokenRepository.saveAndFlush(token);
    }

    @Test
    void rollsBackPasswordAndTokenConsumptionWhenSessionRevocationFails() throws Exception {
        doThrow(new IllegalStateException("simulated revocation failure"))
                .when(authSessionService).revokeAllForUser(anyLong());
        Cookie csrf = requestCsrfCookie();

        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"atomic-token","newPassword":"changed-password"}
                                """))
                .andExpect(status().isInternalServerError());

        User persistedUser = userRepository.findById(userId).orElseThrow();
        PasswordResetToken persistedToken = tokenRepository.findByTokenHash(
                DigestUtils.sha256Hex("atomic-token")).orElseThrow();
        assertThat(passwordEncoder.matches("original-password", persistedUser.getPasswordHash())).isTrue();
        assertThat(persistedToken.getUsedAt()).isNull();
    }

    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrf = result.getResponse().getCookie(AuthCookieService.XSRF_TOKEN);
        assertThat(csrf).isNotNull();
        return csrf;
    }
}
