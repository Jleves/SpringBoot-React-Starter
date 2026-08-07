package com.ashenox.starter.user.support;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        return normalized;
    }
}
