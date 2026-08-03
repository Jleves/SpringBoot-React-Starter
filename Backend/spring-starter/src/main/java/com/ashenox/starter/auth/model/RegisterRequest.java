package com.ashenox.starter.auth.model;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "El nombre de usuario no puede estar vacío")
    @Column(nullable = false)
    String username;
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @NotBlank(message = "La contraseña no puede estar vacía")
    String password;
    @NotBlank(message = "El email no puede estar vacío")
    @Email(message = "El formato del email no es válido")
    String email;
}
