package com.ashenox.starter.auth.service.impl;

import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.service.IssuedAuthentication;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.security.jwt.JWTUtil;
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
    private final AuthenticationManager authenticationManager;
    private final com.ashenox.starter.user.repository.UserRepository userRepository;

    @org.springframework.transaction.annotation.Transactional
    public IssuedAuthentication login(LoginRequest loginRequest) {
        try {
            userRepository.lockByEmail(com.ashenox.starter.user.support.EmailNormalizer.normalize(loginRequest.getEmail()));
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User user = userService.findByEmail(principal.getUsername()).orElseThrow();
            IssuedSession issuedSession = authSessionService.createSession(user);

            String sessionId = issuedSession.session().getId();
            return new IssuedAuthentication(
                    UserDTO.fromUser(user),
                    jwtUtil.generateToken(principal, sessionId),
                    issuedSession.refreshToken(),
                    sessionId
            );
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }
    }
}
