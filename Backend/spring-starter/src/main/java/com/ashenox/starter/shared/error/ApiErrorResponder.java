package com.ashenox.starter.shared.error;

import com.ashenox.starter.log.filter.RequestLoggingFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiErrorResponder {

    private final ObjectMapper objectMapper;

    public ApiError create(
            HttpServletRequest request,
            int status,
            ApiErrorCode code,
            String message
    ) {
        return create(request, status, code, message, List.of());
    }

    public ApiError create(
            HttpServletRequest request,
            int status,
            ApiErrorCode code,
            String message,
            List<ApiFieldError> fieldErrors
    ) {
        return new ApiError(
                Instant.now(),
                status,
                code,
                message,
                request.getRequestURI(),
                RequestLoggingFilter.getRequestId(request),
                fieldErrors
        );
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            ApiErrorCode code,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), create(request, status, code, message));
    }
}
