package com.ashenox.starter.log.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Generar ID único para la request
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);
        
        long startTime = System.currentTimeMillis();
        
        // Log de entrada
        log.info("🌐 REQUEST [{}] {} {} - IP: {}", requestId, method, uri, clientIp);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log de salida
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            
            log.info("✅ RESPONSE [{}] {} {} - Status: {} - {}ms", 
                    requestId, method, uri, status, duration);
            
            // Log especial para errores
            if (status >= 400) {
                log.warn("⚠️ ERROR_RESPONSE [{}] {} {} - Status: {} - {}ms", 
                        requestId, method, uri, status, duration);
            }
            
            MDC.clear();
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}