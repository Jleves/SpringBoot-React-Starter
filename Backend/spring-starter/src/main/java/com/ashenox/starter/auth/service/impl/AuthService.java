package com.ashenox.starter.auth.service.impl;

import com.ashenox.starter.Exception.JWT.InvalidCredentialsException;
import com.ashenox.starter.auth.model.AuthResponse;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JWTUtil jwtUtil;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final AppProperties appProperties;
    private final AuthenticationManager authenticationManager;

    public AuthResponse login(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User user = userService.findByEmail(principal.getUsername()).orElseThrow();
            IssuedSession issuedSession = authSessionService.createSession(user);

            return AuthResponse.builder()
                    .accessToken(jwtUtil.generateToken(principal))
                    .refreshToken(issuedSession.refreshToken())
                    .tokenType("Bearer")
                    .expiresIn(appProperties.getSecurity().getJwt().getAccessExpiration().toSeconds())
                    .user(UserDTO.fromUser(user))
                    .build();
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }
    }
}
