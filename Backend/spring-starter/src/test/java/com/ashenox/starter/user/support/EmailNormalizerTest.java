package com.ashenox.starter.user.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNormalizerTest {

    @Test
    void trimsAndLowercasesEmail() {
        assertThat(EmailNormalizer.normalize("  Usuario@Ejemplo.COM "))
                .isEqualTo("usuario@ejemplo.com");
    }

    @Test
    void rejectsNullOrBlankEmail() {
        assertThatThrownBy(() -> EmailNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailNormalizer.normalize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
