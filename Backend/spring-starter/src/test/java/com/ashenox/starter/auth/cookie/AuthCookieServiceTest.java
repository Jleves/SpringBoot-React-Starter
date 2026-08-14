package com.ashenox.starter.auth.cookie;

import com.ashenox.starter.shared.config.AppProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    private AppProperties properties;
    private AuthCookieService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getSecurity().getCookies().setSecure(false);
        properties.getSecurity().getJwt().setAccessExpiration(Duration.ofMinutes(15));
        properties.getSecurity().getJwt().setRefreshExpiration(Duration.ofHours(3));
        service = new AuthCookieService(properties);
    }

    @Test
    void createsAccessCookieWithSharedPolicy() {
        ResponseCookie cookie = service.accessToken("access-value");

        assertThat(cookie.getName()).isEqualTo(AuthCookieService.ACCESS_TOKEN);
        assertThat(cookie.getValue()).isEqualTo("access-value");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void createsRefreshCookieWithRestrictedPath() {
        ResponseCookie cookie = service.refreshToken("session.secret");

        assertThat(cookie.getName()).isEqualTo(AuthCookieService.REFRESH_TOKEN);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void createsDeletionCookiesWithMatchingPaths() {
        assertThat(service.deleteAccessToken().getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(service.deleteAccessToken().getPath()).isEqualTo("/");
        assertThat(service.deleteRefreshToken().getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(service.deleteRefreshToken().getPath()).isEqualTo("/api/auth");
        assertThat(service.deleteXsrfToken().isHttpOnly()).isFalse();
    }

    @Test
    void readsAuthenticationCookiesFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookieService.ACCESS_TOKEN, "access"),
                new Cookie(AuthCookieService.REFRESH_TOKEN, "refresh")
        );

        assertThat(service.readAccessToken(request)).contains("access");
        assertThat(service.readRefreshToken(request)).contains("refresh");
    }
}
