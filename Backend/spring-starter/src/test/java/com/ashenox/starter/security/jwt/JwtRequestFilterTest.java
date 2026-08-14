package com.ashenox.starter.security.jwt;

import com.ashenox.starter.security.service.DatabaseUserDetailsService;
import com.ashenox.starter.shared.error.ApiErrorResponder;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtRequestFilterTest {

    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final JwtRequestFilter filter = new JwtRequestFilter(
            userDetailsService, jwtUtil, new ApiErrorResponder(new ObjectMapper()));

    @Test
    void returnsSharedContractForExpiredBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/profile");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.extractUserName("expired-token")).thenThrow(mock(ExpiredJwtException.class));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_EXPIRED", "requestId", "path");
        verifyNoInteractions(userDetailsService);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void returnsSharedContractForInvalidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/profile");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.extractUserName("invalid-token")).thenThrow(new IllegalArgumentException("token details"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("AUTH_TOKEN_INVALID", "requestId", "path")
                .doesNotContain("token details");
        verifyNoInteractions(userDetailsService);
        assertThat(chain.getRequest()).isNull();
    }
}
