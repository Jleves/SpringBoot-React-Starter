package com.ashenox.starter.security.jwt;





import com.ashenox.starter.user.service.impl.UserServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private JWTUtil jwtUtil;

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String username = null;
        String jwt = null;

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                jwt = authorizationHeader.substring(7);
                username = jwtUtil.extractUserName(jwt);
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }

            filterChain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();

            String ip = resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");

            securityLog.warn("JWT expirado - IP [{}] - URI [{}] - UA [{}]",
                    ip,
                    request.getRequestURI(),
                    userAgent
            );

            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expirado");

        } catch (io.jsonwebtoken.MalformedJwtException | io.jsonwebtoken.security.SignatureException ex) {
            SecurityContextHolder.clearContext();

            String ip = resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");

            securityLog.warn("JWT inválido o manipulado - IP [{}] - URI [{}] - UA [{}]",
                    ip,
                    request.getRequestURI(),
                    userAgent
            );

            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o manipulado");

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();

            String ip = resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");

            securityLog.error("Error inesperado procesando JWT - IP [{}] - URI [{}] - UA [{}]",
                    ip,
                    request.getRequestURI(),
                    userAgent,
                    ex
            );

            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Error al procesar el token");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String json = """
                {
                  "timestamp": "%s",
                  "status": %d,
                  "mensaje": "%s"
                }
                """.formatted(java.time.LocalDate.now(), status, message);

        response.getWriter().write(json);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }
}


