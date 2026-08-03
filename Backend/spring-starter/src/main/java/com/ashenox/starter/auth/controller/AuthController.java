package com.ashenox.starter.auth.controller;



import com.ashenox.starter.auth.model.AuthResponse;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.model.TokenRefreshRequest;
import com.ashenox.starter.auth.model.TokenRefreshResponse;
import com.ashenox.starter.auth.passwordreset.dto.ForgotPasswordRequest;
import com.ashenox.starter.auth.passwordreset.dto.ResetPasswordRequest;
import com.ashenox.starter.auth.service.impl.AuthService;
import com.ashenox.starter.auth.service.impl.PasswordResetServiceImpl;
import com.ashenox.starter.auth.service.impl.RefreshTokenService;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.impl.UserServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");
    private final AuthService authService;
    private final UserServiceImpl userService;
    private final JWTUtil jwtUtil;
    private final PasswordResetServiceImpl passwordResetService;
    private RefreshTokenService refreshTokenService;

@Autowired
    public AuthController(AuthService authService, UserServiceImpl userService, JWTUtil jwtUtil, PasswordResetServiceImpl passwordResetService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    this.passwordResetService = passwordResetService;
    this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest){
        String username = loginRequest.getEmail();

        securityLog.info("LOGIN_ATTEMPT - Usuario: {}", username);
        log.info("Intento de login para usuario: {}", username);
        
        try {
            AuthResponse response = authService.login(loginRequest);
            securityLog.info("LOGIN_SUCCESS - Usuario: {}", username);
            log.info("Login exitoso para usuario: {}", username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            securityLog.warn("LOGIN_FAILED - Usuario: {} - Error: {}", username, e.getMessage());
            log.warn("Login fallido para usuario: {} - {}", username, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestToken = request.getRefreshToken();
        
        securityLog.info("TOKEN_REFRESH_ATTEMPT - Token: {}...", requestToken.substring(0, Math.min(10, requestToken.length())));
        log.info("Intento de refresh token");

        return refreshTokenService.findByToken(requestToken)
                .map(refreshTokenService::verifyExpiration)
                .map(refreshToken -> {
                    // Eliminamos o invalidamos el token usado
                    refreshTokenService.delete(refreshToken.getUser().getId());

                    // Creamos nuevos tokens
                    User user = refreshToken.getUser();
                    UserDetails userDetails = user;

                    String newAccessToken = jwtUtil.generateToken(userDetails);
                    String newRefreshToken = refreshTokenService.createRefreshToken(user.getUsername(), user.getId()).getToken();

                    securityLog.info("TOKEN_REFRESH_SUCCESS - Usuario: {}", user.getUsername());
                    log.info("Token refresh exitoso para usuario: {}", user.getUsername());
                    return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken, newRefreshToken));
                })
                .orElseThrow(() -> {
                    securityLog.warn("TOKEN_REFRESH_FAILED - Token inválido");
                    log.warn("Refresh token inválido");
                    return new RuntimeException("Refresh token inválido");
                });
    }


//Pedido regeneracion de clave
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail();
        
        securityLog.info("PASSWORD_RESET_REQUEST - Email: {}", email);
        log.info("Solicitud de reset de contraseña para: {}", email);
        
        passwordResetService.createPasswordResetToken(email);
        return ResponseEntity.ok("Si el correo existe, recibirás instrucciones.");
    }

    @GetMapping("/reset-password")
    public ResponseEntity<String> validateToken(@RequestParam String token) {
        log.info("validation token");
       passwordResetService.validateToken(token);
        return ResponseEntity.ok("Token válido. Mostrar formulario de cambio.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String token = request.getToken();
        
        securityLog.info("PASSWORD_RESET_ATTEMPT - Token: {}...", token.substring(0, Math.min(10, token.length())));
        log.info("Intento de reset de contraseña");
        
        try {
            passwordResetService.resetPassword(token, request.getNewPassword());
            securityLog.info("PASSWORD_RESET_SUCCESS - Token: {}...", token.substring(0, Math.min(10, token.length())));
            log.info("Reset de contraseña exitoso");
            return ResponseEntity.ok("Contraseña actualizada con éxito.");
        } catch (Exception e) {
            securityLog.warn("PASSWORD_RESET_FAILED - Token: {}... - Error: {}", token.substring(0, Math.min(10, token.length())), e.getMessage());
            log.warn("Reset de contraseña fallido: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();
        Optional<User> optionalUser = userService.findByEmail(username);


        if (optionalUser.isPresent()) {
            UserDTO userDTO = UserDTO.fromUser(optionalUser.get());
            return ResponseEntity.ok(userDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}


