package com.ashenox.starter.security.error;

import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.shared.error.ApiErrorResponder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlersTest {

    private static final String REQUEST_ID = "security-request-123";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiErrorResponder responder = new ApiErrorResponder(objectMapper);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
        response = new MockHttpServletResponse();
    }

    @Test
    void authenticationEntryPointReturnsSharedUnauthorizedResponse() throws Exception {
        new ApiAuthenticationEntryPoint(responder).commence(
                request, response, new BadCredentialsException("sensitive"));

        assertResponse(401, ApiErrorCode.AUTH_REQUIRED);
    }

    @Test
    void accessDeniedHandlerReturnsSharedForbiddenResponse() throws Exception {
        new ApiAccessDeniedHandler(responder).handle(
                request, response, new AccessDeniedException("sensitive"));

        assertResponse(403, ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void accessDeniedHandlerReturnsSpecificCsrfResponse() throws Exception {
        new ApiAccessDeniedHandler(responder).handle(
                request, response, new MissingCsrfTokenException(null));

        assertResponse(403, ApiErrorCode.CSRF_TOKEN_INVALID);
    }

    private void assertResponse(int status, ApiErrorCode code) throws Exception {
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(status);
        assertThat(json.get("code").asText()).isEqualTo(code.name());
        assertThat(json.get("message").asText()).isNotBlank();
        assertThat(json.get("path").asText()).isEqualTo("/api/private");
        assertThat(json.get("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(json.has("fieldErrors")).isFalse();
        assertThat(response.getContentAsString()).doesNotContain("sensitive");
    }
}
