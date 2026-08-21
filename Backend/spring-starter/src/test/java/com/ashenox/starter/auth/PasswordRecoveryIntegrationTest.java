package com.ashenox.starter.auth;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.port.PasswordResetEmailSender;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.password-reset.token-ttl=5m")
class PasswordRecoveryIntegrationTest {

    private static final String EMAIL = "recovery@example.com";
    private static final String PASSWORD = "original-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppProperties appProperties;

    @MockitoBean
    private PasswordResetEmailSender emailSender;

    @BeforeEach
    void setUp() {
        reset(emailSender);
        tokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .enabled(true)
                .build());
    }

    @Test
    void forgotPasswordIsAntiEnumerationAndUsesConfiguredTtl() throws Exception {
        Cookie csrf = requestCsrfCookie();
        Instant before = Instant.now();

        forgotPassword(csrf, EMAIL)
                .andExpect(status().isOk())
                .andExpect(content().string("Si el correo existe, recibirás instrucciones."));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3_000)).send(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(EMAIL), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotBlank();

        PasswordResetToken stored = tokenRepository.findByUser(
                userRepository.findByEmail(EMAIL).orElseThrow()).orElseThrow();
        assertThat(stored.getExpiresAt()).isBetween(
                before.plus(Duration.ofMinutes(5)).minusSeconds(2),
                before.plus(Duration.ofMinutes(5)).plusSeconds(2));
        assertThat(appProperties.getSecurity().getPasswordReset().getTokenTtl())
                .isEqualTo(Duration.ofMinutes(5));

        reset(emailSender);
        forgotPassword(csrf, "missing@example.com")
                .andExpect(status().isOk())
                .andExpect(content().string("Si el correo existe, recibirás instrucciones."));
        verifyNoInteractions(emailSender);
    }

    @Test
    void smtpFailureDoesNotChangePublicResponseAndAnotherRequestReplacesToken() throws Exception {
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        Cookie csrf = requestCsrfCookie();

        forgotPassword(csrf, EMAIL)
                .andExpect(status().isOk())
                .andExpect(content().string("Si el correo existe, recibirás instrucciones."));
        verify(emailSender, timeout(3_000)).send(anyString(), anyString(), anyString());
        String firstHash = tokenRepository.findByUser(
                userRepository.findByEmail(EMAIL).orElseThrow()).orElseThrow().getTokenHash();

        reset(emailSender);
        forgotPassword(csrf, EMAIL).andExpect(status().isOk());
        verify(emailSender, timeout(3_000)).send(anyString(), anyString(), anyString());
        String secondHash = tokenRepository.findByUser(
                userRepository.findByEmail(EMAIL).orElseThrow()).orElseThrow().getTokenHash();
        assertThat(secondHash).isNotEqualTo(firstHash);
    }

    @Test
    void resetConsumesTokenAndRevokesAllSessions() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        String firstSessionId = authSessionService.createSession(user).session().getId();
        String secondSessionId = authSessionService.createSession(user).session().getId();
        Cookie csrf = requestCsrfCookie();
        forgotPassword(csrf, EMAIL).andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3_000)).send(anyString(), anyString(), tokenCaptor.capture());
        String token = tokenCaptor.getValue();

        mockMvc.perform(get("/api/auth/reset-password/validate").param("token", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"new-secure-password"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("new-secure-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(tokenRepository.findByUser(user).orElseThrow().getUsedAt()).isNotNull();
        assertThat(sessionRepository.findById(firstSessionId).map(AuthSession::getRevokedAt)).isPresent();
        assertThat(sessionRepository.findById(secondSessionId).map(AuthSession::getRevokedAt)).isPresent();

        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"another-password"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setTokenHash(org.apache.commons.codec.digest.DigestUtils.sha256Hex("expired-token"));
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        tokenRepository.saveAndFlush(expired);

        mockMvc.perform(get("/api/auth/reset-password/validate").param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions forgotPassword(Cookie csrf, String email)
            throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
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
