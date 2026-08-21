package com.ashenox.starter.auth.passwordreset.event;

public record PasswordResetRequested(Long userId, String recipient, String displayName, String token) {
}
