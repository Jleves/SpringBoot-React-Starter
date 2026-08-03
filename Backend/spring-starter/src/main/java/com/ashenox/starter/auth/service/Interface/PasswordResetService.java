package com.ashenox.starter.auth.service;

public interface PasswordResetService {

    void createPasswordResetToken(String email);
    boolean validateToken(String token);
    void resetPassword(String token, String newPassword);
}
