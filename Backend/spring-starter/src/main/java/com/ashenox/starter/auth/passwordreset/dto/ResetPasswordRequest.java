package com.ashenox.starter.auth.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String newPassword;
}
