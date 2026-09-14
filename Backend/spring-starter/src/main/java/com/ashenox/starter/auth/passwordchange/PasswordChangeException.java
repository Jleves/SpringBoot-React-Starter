package com.ashenox.starter.auth.passwordchange;

public class PasswordChangeException extends RuntimeException {
    private final String field;
    private final long retryAfter;

    public PasswordChangeException(String field, String message, long retryAfter) {
        super(message);
        this.field = field;
        this.retryAfter = retryAfter;
    }

    public String getField() { return field; }
    public long getRetryAfter() { return retryAfter; }
}
