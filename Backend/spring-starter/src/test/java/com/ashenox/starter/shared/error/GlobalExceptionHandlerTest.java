package com.ashenox.starter.shared.error;

import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.security.error.InvalidTokenException;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID = "request-123";
    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ApiErrorResponder(new ObjectMapper()));
        request = new MockHttpServletRequest("POST", "/test/errors");
        request.setAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, REQUEST_ID);
    }

    @Test
    void returnsValidationErrorWithFieldErrors() {
        LoginRequest target = new LoginRequest("invalid", "");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "loginRequest");
        bindingResult.rejectValue("email", "Email", "El email no tiene un formato valido");
        bindingResult.rejectValue("password", "NotBlank", "La contrasena es obligatoria");

        ResponseEntity<ApiError> response = handler.handleValidation(
                new MethodArgumentNotValidException(null, bindingResult), request);

        assertError(response, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiFieldError::field)
                .containsExactly("email", "password");
    }

    @Test
    void returnsMalformedRequest() {
        assertError(handler.handleMalformedRequest(new RuntimeException(), request),
                HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void returnsInvalidCredentials() {
        assertError(handler.handleInvalidCredentials(new InvalidCredentialsException("sensitive"), request),
                HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void returnsInvalidResetToken() {
        assertError(handler.handleInvalidResetToken(new InvalidTokenException("sensitive"), request),
                HttpStatus.BAD_REQUEST, ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
    }

    @Test
    void returnsInvalidRefreshToken() {
        assertError(handler.handleInvalidRefreshToken(
                        new InvalidRefreshTokenException("sensitive"), request),
                HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void returnsAccessDenied() {
        assertError(handler.handleAccessDenied(new AccessDeniedException("sensitive"), request),
                HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void returnsResourceNotFound() {
        ResponseEntity<ApiError> response = handler.handleNotFound(
                new ResourceNotFoundException("El usuario no existe."), request);

        assertError(response, HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("El usuario no existe.");
    }

    @Test
    void returnsMethodNotAllowed() {
        assertError(handler.handleMethodNotAllowed(
                        new HttpRequestMethodNotSupportedException("PATCH"), request),
                HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void returnsResourceConflict() {
        assertError(handler.handleConflict(new DataIntegrityViolationException("database details"), request),
                HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void returnsBusinessValidationError() {
        ResponseEntity<ApiError> response = handler.handleBusinessError(
                new BusinessException("La operacion no es valida."), request);

        assertError(response, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().message()).isEqualTo("La operacion no es valida.");
    }

    @Test
    void returnsSanitizedInternalError() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new RuntimeException("internal implementation details"), request);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("Ocurrió un error interno.")
                .doesNotContain("implementation details");
    }

    private void assertError(ResponseEntity<ApiError> response, HttpStatus status, ApiErrorCode code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        ApiError error = response.getBody();
        assertThat(error).isNotNull();
        assertThat(error.timestamp()).isNotNull();
        assertThat(error.status()).isEqualTo(status.value());
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.message()).isNotBlank();
        assertThat(error.path()).isEqualTo("/test/errors");
        assertThat(error.requestId()).isEqualTo(REQUEST_ID);
        assertThat(error.fieldErrors()).isNotNull();
    }
}
