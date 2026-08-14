package com.ashenox.starter.auth;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCookieFlowIntegrationTest {

    private static final String EMAIL = "cookie-user@example.com";
    private static final String PASSWORD = "secure-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
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
    void completesLoginMeRefreshAndLogoutUsingOnlyCookies() throws Exception {
        Cookie csrf = requestCsrfCookie();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthCookieService.ACCESS_TOKEN))
                .andExpect(cookie().exists(AuthCookieService.REFRESH_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieService.ACCESS_TOKEN, true))
                .andExpect(cookie().httpOnly(AuthCookieService.REFRESH_TOKEN, true))
                .andExpect(cookie().secure(AuthCookieService.ACCESS_TOKEN, false))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        Cookie refresh = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(AuthCookieService.ACCESS_TOKEN))
                .andExpect(cookie().exists(AuthCookieService.REFRESH_TOKEN))
                .andReturn();

        Cookie rotatedRefresh = rotated.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(rotatedRefresh).isNotNull();
        assertThat(rotatedRefresh.getValue()).isNotEqualTo(refresh.getValue());

        String sessionId = rotatedRefresh.getValue().substring(0, rotatedRefresh.getValue().indexOf('.'));
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrf, rotatedRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.XSRF_TOKEN, 0));

        AuthSession revoked = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    void rejectsMutationWithoutCsrfUsingSharedErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void reusingRotatedRefreshTokenPersistsSessionRevocation() throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult login = performLogin(csrf);
        Cookie originalRefresh = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(originalRefresh).isNotNull();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, originalRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, originalRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));

        String sessionId = originalRefresh.getValue().substring(0, originalRefresh.getValue().indexOf('.'));
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void doesNotAcceptBearerAsAlternativeAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer ignored"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void logoutWithoutSessionIsIdempotentAndStillClearsCookies() throws Exception {
        Cookie csrf = requestCsrfCookie();

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_TOKEN, 0));
    }

    @Test
    void passwordResetConsumesTokenAndRevokesEveryUserSession() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        String firstSessionId = authSessionService.createSession(user).session().getId();
        String secondSessionId = authSessionService.createSession(user).session().getId();
        String plainResetToken = "plain-reset-token";

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(DigestUtils.sha256Hex(plainResetToken));
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));
        resetTokenRepository.saveAndFlush(resetToken);
        Cookie csrf = requestCsrfCookie();

        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"plain-reset-token","newPassword":"new-secure-password"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("new-secure-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(resetTokenRepository.findByTokenHash(DigestUtils.sha256Hex(plainResetToken))
                .orElseThrow().getUsedAt()).isNotNull();
        assertThat(sessionRepository.findById(firstSessionId).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessionRepository.findById(secondSessionId).orElseThrow().getRevokedAt()).isNotNull();
    }

    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(AuthCookieService.XSRF_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieService.XSRF_TOKEN, false))
                .andReturn();
        Cookie csrf = result.getResponse().getCookie(AuthCookieService.XSRF_TOKEN);
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private MvcResult performLogin(Cookie csrf) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
    }
}
