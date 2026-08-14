package com.ashenox.starter.auth.controller;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.model.LoginResponse;
import com.ashenox.starter.auth.passwordreset.dto.ForgotPasswordRequest;
import com.ashenox.starter.auth.passwordreset.dto.ResetPasswordRequest;
import com.ashenox.starter.auth.service.IssuedAuthentication;
import com.ashenox.starter.auth.service.impl.AuthService;
import com.ashenox.starter.auth.service.impl.PasswordResetServiceImpl;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final AuthService authService;
    private final UserService userService;
    private final JWTUtil jwtUtil;
    private final PasswordResetServiceImpl passwordResetService;
    private final AuthSessionService authSessionService;
    private final AuthCookieService cookieService;

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        IssuedAuthentication issued = authService.login(request);
        SECURITY_LOG.info("event=login_success userId={} sessionId={}",
                issued.user().getId(), issued.sessionId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.accessToken(issued.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.refreshToken(issued.refreshToken()).toString())
                .body(new LoginResponse(issued.user()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
        String refreshToken = cookieService.readRefreshToken(request)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token ausente"));
        IssuedSession issuedSession = authSessionService.rotate(refreshToken);
        User user = issuedSession.session().getUser();
        UserDetails principal = AuthenticatedUser.from(user);
        String accessToken = jwtUtil.generateToken(principal, issuedSession.session().getId());
        SECURITY_LOG.info("event=token_refresh_success userId={} sessionId={}",
                user.getId(), issuedSession.session().getId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.accessToken(accessToken).toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.refreshToken(issuedSession.refreshToken()).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookieService.readRefreshToken(request).ifPresent(authSessionService::revoke);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.deleteAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.deleteRefreshToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.deleteXsrfToken().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.createPasswordResetToken(request.getEmail());
        return ResponseEntity.ok("Si el correo existe, recibirás instrucciones.");
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<Void> validateToken(@RequestParam String token) {
        passwordResetService.validateToken(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal UserDetails principal) {
        return userService.findByEmail(principal.getUsername())
                .map(UserDTO::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
