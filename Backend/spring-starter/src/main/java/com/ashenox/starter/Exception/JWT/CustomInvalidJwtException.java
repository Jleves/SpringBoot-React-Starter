package com.ashenox.starter.Exception.JWT;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CustomInvalidJwtException extends RuntimeException {
    public CustomInvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}