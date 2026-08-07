package com.ashenox.starter.shared.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private Security security = new Security();

    @Valid
    private Mail mail = new Mail();

    @Valid
    private Init init = new Init();

    @Getter
    @Setter
    public static class Security {
        @Valid
        private Jwt jwt = new Jwt();
    }

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;

        @NotNull
        private Duration accessExpiration = Duration.ofMinutes(15);

        @NotNull
        private Duration refreshExpiration = Duration.ofHours(3);

        @AssertTrue(message = "las duraciones de JWT deben ser mayores que cero")
        public boolean isExpirationConfigurationValid() {
            return accessExpiration != null && !accessExpiration.isZero() && !accessExpiration.isNegative()
                    && refreshExpiration != null && !refreshExpiration.isZero() && !refreshExpiration.isNegative();
        }

        @AssertTrue(message = "app.security.jwt.secret debe ser Base64 y contener al menos 256 bits")
        public boolean isSecretValid() {
            if (secret == null || secret.isBlank()) {
                return true;
            }
            try {
                return Decoders.BASE64.decode(secret).length >= 32;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    @Getter
    @Setter
    public static class Mail {
        @NotBlank
        private String host;

        @Min(1)
        @Max(65_535)
        private int port = 587;

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        @NotNull
        private URI verificationBaseUrl;

        @NotNull
        private URI frontendBaseUrl;
    }

    @Getter
    @Setter
    public static class Init {
        @Valid
        private SuperAdmin superadmin = new SuperAdmin();
    }

    @Getter
    @Setter
    public static class SuperAdmin {
        private boolean enabled;

        @Email
        private String email;

        private String password;

        @AssertTrue(message = "email y password son obligatorios cuando el bootstrap de SUPER_ADMIN está habilitado")
        public boolean isConfigurationValid() {
            if (!enabled) {
                return true;
            }
            return email != null && !email.isBlank()
                    && password != null && password.length() >= 12 && password.length() <= 72;
        }
    }
}
