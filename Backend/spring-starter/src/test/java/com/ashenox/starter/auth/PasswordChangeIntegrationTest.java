package com.ashenox.starter.auth;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordchange.ChangePasswordRequest;
import com.ashenox.starter.auth.passwordchange.PasswordChangeService;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordChangeService changes;
    @Autowired com.ashenox.starter.auth.service.impl.AuthService authService;
    @Autowired com.ashenox.starter.auth.session.service.AuthSessionService sessionService;
    @Autowired com.ashenox.starter.auth.service.PasswordResetService resets;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    Long id;
    Cookie csrf;

    @BeforeEach
    void setup() throws Exception {
        tokens.deleteAll(); sessions.deleteAll(); users.deleteAll();
        id = users.saveAndFlush(User.builder().email("change@example.com")
                .passwordHash(encoder.encode("original-password")).role(Role.USER).enabled(true).build()).getId();
        csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    Cookie[] login(String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"change@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Cookie[]{result.getCookie("ACCESS_TOKEN"), result.getCookie("REFRESH_TOKEN")};
    }

    ResultActions change(Cookie access, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/change-password").cookie(csrf, access)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void changesOwnPasswordAndInvalidatesPreviousCredentials(Role role) throws Exception {
        User user = users.findById(id).orElseThrow(); user.setRole(role); users.saveAndFlush(user);
        var first = login("original-password"); var second = login("original-password");
        PasswordResetToken token = new PasswordResetToken(); token.setUser(user);
        token.setTokenHash(DigestUtils.sha256Hex("old-link")); token.setExpiresAt(Instant.now().plusSeconds(600));
        tokens.saveAndFlush(token);
        change(first[0], "original-password", "changed-password").andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("ACCESS_TOKEN", 0)).andExpect(cookie().maxAge("REFRESH_TOKEN", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
        assertThat(sessions.findAll()).allMatch(s -> s.getRevokedAt() != null);
        assertThat(tokens.findByTokenHash(DigestUtils.sha256Hex("old-link")).orElseThrow().getUsedAt()).isNotNull();
        mvc.perform(post("/api/auth/refresh").cookie(csrf, second[1]).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/reset-password/validate").param("token", "old-link"))
                .andExpect(status().isBadRequest());
        // Existing stateless access token retains its documented residual lifetime.
        mvc.perform(get("/api/auth/me").cookie(second[0])).andExpect(status().isOk());
        assertThat(encoder.matches("original-password", users.findById(id).orElseThrow().getPasswordHash())).isFalse();
        login("changed-password");
    }

    @Test void throttlesPersistentlyAndAllowsRetryAfterWindow() throws Exception {
        var auth = login("original-password");
        for (int i = 0; i < 5; i++) change(auth[0], "wrong-password", "changed-password")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        change(auth[0], "original-password", "changed-password").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(sessions.findAll()).allMatch(s -> s.getRevokedAt() == null);
        var user = users.findById(id).orElseThrow(); user.setPasswordChangeWindowStart(Instant.now().minusSeconds(901)); users.saveAndFlush(user);
        change(auth[0], "original-password", "changed-password").andExpect(status().isNoContent());
    }

    @Test void requiresAuthenticationCsrfAndValidNewPassword() throws Exception {
        mvc.perform(post("/api/auth/change-password").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
        var auth = login("original-password");
        mvc.perform(post("/api/auth/change-password").cookie(auth[0])
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        for (String next : new String[]{"short", "original-password", "é".repeat(40)})
            change(auth[0], "original-password", next).andExpect(status().isBadRequest());
        assertThat(encoder.matches("original-password", users.findById(id).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void onlyOneConcurrentChangeCanUseTheOriginalPassword() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> operation = () -> {
                start.await();
                try { changes.change(id, new ChangePasswordRequest("original-password", "changed-password")); return true; }
                catch (com.ashenox.starter.auth.passwordchange.PasswordChangeException expected) { return false; }
            };
            var first = executor.submit(operation); var second = executor.submit(operation); start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void suppliedUserIdCannotChangeAnotherAccount() throws Exception {
        var other = users.saveAndFlush(User.builder().email("other@example.com")
                .passwordHash(encoder.encode("other-password")).role(Role.USER).enabled(true).build());
        var auth = login("original-password");
        mvc.perform(post("/api/auth/change-password").cookie(csrf, auth[0]).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + other.getId()
                        + ",\"currentPassword\":\"original-password\",\"newPassword\":\"changed-password\"}"))
                .andExpect(status().isNoContent());
        assertThat(encoder.matches("other-password", users.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(encoder.matches("changed-password", users.findById(id).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void rejectsDisabledUserWithAnExistingAccessToken() throws Exception {
        var auth = login("original-password");
        var user = users.findById(id).orElseThrow(); user.setEnabled(false); users.saveAndFlush(user);
        change(auth[0], "original-password", "changed-password").andExpect(status().isUnauthorized());
    }

    @Test void loginWaitingForPasswordChangeCannotAuthenticateWithOldPassword() throws Exception {
        CountDownLatch changed = new CountDownLatch(1), loginStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        changes.change(id, new ChangePasswordRequest("original-password", "changed-password"));
                        changed.countDown();
                        try { if (!loginStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("login did not start"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    }));
            var second = executor.submit(() -> {
                if (!changed.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("change did not start");
                loginStarted.countDown();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.login(
                        new com.ashenox.starter.auth.model.LoginRequest("change@example.com", "original-password")))
                        .isInstanceOf(com.ashenox.starter.security.error.InvalidCredentialsException.class);
                return true;
            });
            first.get(10, java.util.concurrent.TimeUnit.SECONDS); second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(sessions.findAll()).isEmpty();
    }

    @Test void refreshRacingWithChangeCannotLeaveAnActiveSession() throws Exception {
        var auth = login("original-password");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); changes.change(id, new ChangePasswordRequest("original-password", "changed-password")); return true; });
            var second = executor.submit(() -> {
                start.await();
                try { sessionService.rotate(auth[1].getValue()); }
                catch (com.ashenox.starter.security.error.InvalidRefreshTokenException expected) { /* Rejected after change committed. */ }
                return true;
            });
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS); second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(sessions.findAll()).allMatch(s -> s.getRevokedAt() != null);
    }

    @Test void resetAndChangeCannotBothUseThePreviousCredentials() throws Exception {
        var token = new PasswordResetToken(); token.setUser(users.findById(id).orElseThrow());
        token.setTokenHash(DigestUtils.sha256Hex("racing-reset")); token.setExpiresAt(Instant.now().plusSeconds(600));
        tokens.saveAndFlush(token);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                try { changes.change(id, new ChangePasswordRequest("original-password", "changed-password")); return true; }
                catch (com.ashenox.starter.auth.passwordchange.PasswordChangeException expected) { return false; }
            });
            var second = executor.submit(() -> {
                start.await();
                try { resets.resetPassword("racing-reset", "reset-password"); return true; }
                catch (com.ashenox.starter.security.error.InvalidTokenException expected) { return false; }
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(tokens.findByTokenHash(DigestUtils.sha256Hex("racing-reset")).orElseThrow().getUsedAt()).isNotNull();
    }
}
