package com.ashenox.starter.auth.passwordreset.port;

public interface PasswordResetEmailSender {
    void send(String recipient, String displayName, String token);
}
