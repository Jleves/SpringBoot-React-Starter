package com.ashenox.starter.security.jwt;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.security.service.DatabaseUserDetailsService;
import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.shared.error.ApiErrorResponder;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final DatabaseUserDetailsService userDetailsService;
    private final JWTUtil jwtUtil;
    private final ApiErrorResponder errorResponder;
    private final AuthCookieService cookieService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = cookieService.readAccessToken(request).orElse(null);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            authenticate(jwt, request);
        } catch (ExpiredJwtException exception) {
            reject(request, response, ApiErrorCode.AUTH_TOKEN_EXPIRED, "La sesión expiró.");
            return;
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            reject(request, response, ApiErrorCode.AUTH_TOKEN_INVALID, "El token de acceso es inválido.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        String username = jwtUtil.extractUserName(jwt);
        if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!userDetails.isEnabled() || !jwtUtil.isTokenValid(jwt, userDetails)) {
            throw new IllegalArgumentException("JWT subject does not match authenticated user");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (userDetails instanceof AuthenticatedUser user) {
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, user.id());
        }
        String sessionId = jwtUtil.extractSessionId(jwt);
        if (sessionId != null) {
            request.setAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE, sessionId);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        ApiErrorCode code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        SECURITY_LOG.warn("event=authentication_rejected code={} method={} path={}",
                code, request.getMethod(), request.getRequestURI());
        errorResponder.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/csrf")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout")
                || path.equals("/api/auth/forgot-password")
                || path.startsWith("/api/auth/reset-password");
    }
}
