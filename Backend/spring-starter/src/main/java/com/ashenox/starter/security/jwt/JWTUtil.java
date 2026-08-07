package com.ashenox.starter.security.jwt;




import com.ashenox.starter.shared.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
@Service
public class JWTUtil {
    private final AppProperties appProperties;

    @Autowired
    public JWTUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
    }



    private Key getSignInKey() {
        String secret = appProperties.getSecurity().getJwt().getSecret();
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Extrae el username (subject) del token
    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extrae la fecha de expiración
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Método genérico para extraer cualquier claim
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extrae todos los claims del token
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Genera el token a partir del UserDetails
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return generateToken(claims, userDetails);
    }

    // Podés agregar claims personalizados (roles, permisos, etc.)
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long expiracionTime = appProperties.getSecurity().getJwt().getAccessExpiration().toMillis();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiracionTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Valida si el token corresponde al usuario y no está expirado
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUserName(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // Verifica si el token expiró
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

}
