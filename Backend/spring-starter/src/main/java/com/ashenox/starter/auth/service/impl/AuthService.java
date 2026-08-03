package com.ashenox.starter.auth.service.impl;



import com.ashenox.starter.Exception.JWT.InvalidCredentialsException;
import com.ashenox.starter.auth.model.AuthResponse;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.security.config.PasswordEncoder;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.Interface.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JWTUtil jwtUtil;  //Para generar el token
    private final PasswordEncoder passwordEncoder; //Encriptar el TOKEN
    private final UserService userService; //Para buscar el usuario

    private RefreshTokenService refreshTokenService;
    private AppProperties appProperties;


@Autowired
    public AuthService( JWTUtil jwtUtil, PasswordEncoder passwordEncoder, UserService userService, RefreshTokenService refreshTokenService, AppProperties appProperties, AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    this.userService = userService;
    this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
        this.authenticationManager = authenticationManager;
    }

    @Autowired
    private final AuthenticationManager authenticationManager; // Para que se autentique


    public AuthResponse login(LoginRequest loginRequest) {

        try {



            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(),loginRequest.getPassword()));
            log.info("Usuario autenticado con exito");

            // ya autenticado por Spring Security.
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();



            User user = userService.findByEmail(loginRequest.getEmail())
                    .orElseThrow();

            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setRol(user.getRol().getAuthority());


            String token = jwtUtil.generateToken(userDetails);


            String refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername(), user.getId()).getToken();

            log.info("Creacion de token y refresh token ok");

            Long expiration= appProperties.getSecurity().getJwt().getAccessExpirationMinutes()*60;



            return AuthResponse.builder()
                    .accessToken(token)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(expiration)
                    .user(userDTO)
                    .build();


        }catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

    }

}
