package com.ashenox.starter.shared.error;

import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.security.error.InvalidTokenException;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiErrorResponder responder;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                      HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(
                        error.getField(),
                        error.getCode() == null ? "INVALID" : error.getCode(),
                        error.getDefaultMessage() == null ? "Valor inválido." : error.getDefaultMessage()
                ))
                .toList();
        return response(request, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "Hay datos inválidos en la solicitud.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception,
                                                               HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()
                ))
                .toList();
        return response(request, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "Hay datos inválidos en la solicitud.", errors);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST,
                "La solicitud no tiene un formato válido.");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception,
                                                              HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                "Las credenciales son inválidas.");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidResetToken(InvalidTokenException exception,
                                                             HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, ApiErrorCode.AUTH_RESET_TOKEN_INVALID,
                "El token de recuperación es inválido o expiró.");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException exception,
                                                               HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                "El refresh token es inválido o expiró.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception,
                                                       HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                "No tenés permisos para realizar esta acción.");
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ResourceNotFoundException
                ? exception.getMessage()
                : "El recurso solicitado no existe.";
        return response(request, HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                            HttpServletRequest request) {
        return response(request, HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED,
                "El método HTTP no está permitido para este recurso.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception,
                                                    HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CONFLICT,
                "La operación entra en conflicto con el estado actual del recurso.");
    }

    @ExceptionHandler({BusinessException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiError> handleBusinessError(Exception exception, HttpServletRequest request) {
        String message = exception instanceof BusinessException
                ? exception.getMessage()
                : "El archivo supera el tamaño máximo permitido.";
        return response(request, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request error path={} code={}", request.getRequestURI(),
                ApiErrorCode.INTERNAL_ERROR, exception);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "Ocurrió un error interno.");
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
                                               ApiErrorCode code, String message) {
        return response(request, status, code, message, List.of());
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
                                               ApiErrorCode code, String message,
                                               List<ApiFieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(responder.create(request, status.value(), code, message, fieldErrors));
    }
}
