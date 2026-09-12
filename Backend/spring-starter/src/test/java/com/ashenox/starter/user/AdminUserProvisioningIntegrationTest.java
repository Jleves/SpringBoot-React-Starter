package com.ashenox.starter.user;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserProvisioningIntegrationTest {

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

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        saveUser("super@example.com", Role.SUPER_ADMIN);
        saveUser("admin@example.com", Role.ADMIN);
        saveUser("user@example.com", Role.USER);
    }

    @Test
    void superAdminCreatesNormalizedSuperAdminWithoutExposingPassword() throws Exception {
        AuthCookies auth = login("super@example.com");

        mockMvc.perform(post("/api/admin/users")
                        .cookie(auth.csrf(), auth.access())
                        .header("X-XSRF-TOKEN", auth.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "New.Admin@Example.COM",
                                  "password": "initial-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.admin@example.com"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User created = userRepository.findByEmail("new.admin@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("initial-password", created.getPasswordHash())).isTrue();
    }

    @Test
    void superAdminCreatesEveryRoleAndNewAccountCanLogin() throws Exception {
        AuthCookies auth = login("super@example.com");
        for (Role role : Role.values()) {
            String email = "created-" + role.name().toLowerCase() + "@example.com";
            createUser(auth, email, role.name()).andExpect(status().isCreated());
            Cookie csrf = requestCsrfCookie();
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"initial-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            mockMvc.perform(get("/api/auth/me")
                            .cookie(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.rol").value(role.name()));
        }
    }

    @Test
    void adminAndUserCannotCreateAnyRole() throws Exception {
        for (String actor : new String[]{"admin@example.com", "user@example.com"}) {
            AuthCookies auth = login(actor);
            for (Role role : Role.values()) {
                createUser(auth, "forbidden@example.com", role.name())
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                assertThat(userRepository.existsByEmail("forbidden@example.com")).isFalse();
            }
        }
    }

    @Test
    void rejectsInvalidInputWithoutPersistingUsers() throws Exception {
        AuthCookies auth = login("super@example.com");
        for (String body : new String[]{
                userBody("invalid-email", "USER"),
                "{\"email\":\"invalid@example.com\",\"password\":\"short\",\"role\":\"USER\"}",
                "{\"email\":\"invalid@example.com\",\"password\":\"initial-password\"}",
                userBody("invalid@example.com", "UNKNOWN")}) {
            mockMvc.perform(post("/api/admin/users").cookie(auth.csrf(), auth.access())
                            .header("X-XSRF-TOKEN", auth.csrf().getValue())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            assertThat(userRepository.count()).isEqualTo(3);
        }
    }
    @Test
    void rejectsRegularUserAndDuplicateEmail() throws Exception {
        AuthCookies regularUser = login("user@example.com");
        createUser(regularUser, "blocked@example.com", "USER")
                .andExpect(status().isForbidden());

        AuthCookies superAdmin = login("super@example.com");
        createUser(superAdmin, "ADMIN@example.com", "USER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void requiresAuthenticationAndValidCsrf() throws Exception {
        Cookie csrf = requestCsrfCookie();
        mockMvc.perform(post("/api/admin/users")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("anonymous@example.com", "USER")))
                .andExpect(status().isUnauthorized());

        AuthCookies auth = login("super@example.com");
        mockMvc.perform(post("/api/admin/users")
                        .cookie(auth.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("without-csrf@example.com", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions createUser(
            AuthCookies auth, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .cookie(auth.csrf(), auth.access())
                .header("X-XSRF-TOKEN", auth.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userBody(email, role)));
    }

    private String userBody(String email, String role) {
        return """
                {"email":"%s","password":"initial-password","role":"%s"}
                """.formatted(email, role);
    }

    private AuthCookies login(String email) throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        assertThat(access).isNotNull();
        return new AuthCookies(csrf, access);
    }

    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrf = result.getResponse().getCookie(AuthCookieService.XSRF_TOKEN);
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private void saveUser(String email, Role role) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(role)
                .enabled(true)
                .build());
    }

    private record AuthCookies(Cookie csrf, Cookie access) {
    }
}
