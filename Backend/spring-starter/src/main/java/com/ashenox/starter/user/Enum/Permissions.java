package com.ashenox.starter.user.Enum;

public enum Permissions {
    CREATED,
    READ,
    UPDATE,
    DELETE;

    public String getAuthority() {
        return this.name();
    }
}
