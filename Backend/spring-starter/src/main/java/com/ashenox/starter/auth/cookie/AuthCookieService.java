package com.ashenox.starter.auth.cookie;

import com.ashenox.starter.shared.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";
    public static final String XSRF_TOKEN = "XSRF-TOKEN";
    private static final String SAME_SITE = "Lax";

    private final AppProperties appProperties;

    public ResponseCookie accessToken(String value) {
        return authenticationCookie(ACCESS_TOKEN, value, "/",
                appProperties.getSecurity().getJwt().getAccessExpiration());
    }

    public ResponseCookie refreshToken(String value) {
        return authenticationCookie(REFRESH_TOKEN, value, "/api/auth",
                appProperties.getSecurity().getJwt().getRefreshExpiration());
    }

    public ResponseCookie deleteAccessToken() {
        return deleteCookie(ACCESS_TOKEN, "/", true);
    }

    public ResponseCookie deleteRefreshToken() {
        return deleteCookie(REFRESH_TOKEN, "/api/auth", true);
    }

    public ResponseCookie deleteXsrfToken() {
        return deleteCookie(XSRF_TOKEN, "/", false);
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN);
    }

    private ResponseCookie authenticationCookie(String name, String value, String path, Duration maxAge) {
        return baseCookie(name, value, path, true)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie deleteCookie(String name, String path, boolean httpOnly) {
        return baseCookie(name, "", path, httpOnly)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, String path,
                                                             boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(appProperties.getSecurity().getCookies().isSecure())
                .sameSite(SAME_SITE)
                .path(path);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
