package com.ashenox.starter.log.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".requestId";
    public static final String USER_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".userId";
    public static final String SESSION_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".sessionId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    public static String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "unknown" : requestId.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);

        String method = request.getMethod();
        String path = request.getRequestURI();
        long startTime = System.currentTimeMillis();
        LOGGER.info("request_started method={} path={}", method, path);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
            Object sessionId = request.getAttribute(SESSION_ID_ATTRIBUTE);
            LOGGER.info("request_completed method={} path={} status={} durationMs={} userId={} sessionId={}",
                    method, path, response.getStatus(), duration,
                    userId == null ? "anonymous" : userId,
                    sessionId == null ? "none" : sessionId);
            MDC.remove("requestId");
        }
    }
}
