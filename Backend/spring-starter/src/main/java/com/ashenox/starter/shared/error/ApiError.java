package com.ashenox.starter.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        ApiErrorCode code,
        String message,
        String path,
        String requestId,
        List<ApiFieldError> fieldErrors
) {
}
