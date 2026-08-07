package com.ashenox.starter.auth.controller;

import com.ashenox.starter.auth.model.AuthResponse;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.model.TokenRefreshRequest;
import com.ashenox.starter.auth.model.TokenRefreshResponse;
import com.ashenox.starter.auth.passwordreset.dto.ForgotPasswordRequest;
import com.ashenox.starter.auth.passwordreset.dto.ResetPasswordRequest;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.auth.service.impl.AuthService;
import com.ashenox.starter.auth.service.impl.PasswordResetServiceImpl;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final AuthService authService;
    private final UserService userService;
    private final JWTUtil jwtUtil;
    private final PasswordResetServiceImpl passwordResetService;
    private final AuthSessionService authSessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        SECURITY_LOG.info("LOGIN_SUCCESS - Usuario: {}", response.getUser().getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        IssuedSession issuedSession = authSessionService.rotate(request.getRefreshToken());
        User user = issuedSession.session().getUser();
        UserDetails principal = AuthenticatedUser.from(user);
        String accessToken = jwtUtil.generateToken(principal);
        SECURITY_LOG.info("TOKEN_REFRESH_SUCCESS - Usuario: {}", user.getEmail());
        return ResponseEntity.ok(new TokenRefreshResponse(accessToken, issuedSession.refreshToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.createPasswordResetToken(request.getEmail());
        return ResponseEntity.ok("Si el correo existe, recibirás instrucciones.");
    }

    @GetMapping("/reset-password")
    public ResponseEntity<String> validateToken(@RequestParam String token) {
        passwordResetService.validateToken(token);
        return ResponseEntity.ok("Token válido. Mostrar formulario de cambio.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Contraseña actualizada con éxito.");
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getUserProfile(@AuthenticationPrincipal UserDetails principal) {
        return userService.findByEmail(principal.getUsername())
                .map(UserDTO::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
