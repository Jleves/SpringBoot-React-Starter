package com.ashenox.starter.user.model;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }
}
