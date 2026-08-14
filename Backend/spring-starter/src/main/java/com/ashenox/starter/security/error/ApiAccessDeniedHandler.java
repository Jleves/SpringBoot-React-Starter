package com.ashenox.starter.security.error;

import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.shared.error.ApiErrorResponder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponder responder;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        responder.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                ApiErrorCode.ACCESS_DENIED, "No tenés permisos para realizar esta acción.");
    }
}
