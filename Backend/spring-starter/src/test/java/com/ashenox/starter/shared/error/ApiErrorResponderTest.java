package com.ashenox.starter.shared.error;

import com.ashenox.starter.log.filter.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiErrorResponder responder = new ApiErrorResponder(objectMapper);

    @Test
    void writesTheSharedErrorContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        responder.write(request, response, 401, ApiErrorCode.AUTH_REQUIRED,
                "Se requiere autenticación.");

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("code").asText()).isEqualTo("AUTH_REQUIRED");
        assertThat(json.get("message").asText()).isEqualTo("Se requiere autenticación.");
        assertThat(json.get("path").asText()).isEqualTo("/api/private");
        assertThat(json.get("requestId").asText()).isEqualTo("request-123");
        assertThat(json.has("fieldErrors")).isFalse();
    }
}
