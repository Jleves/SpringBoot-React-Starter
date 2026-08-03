package com.ashenox.starter.user.Enum;

import java.util.Set;

public enum Role {
    SUPERADMINISTRADOR(Set.of(Permissions.CREATED,Permissions.READ,Permissions.UPDATE,Permissions.DELETE)),
    ADMIN(Set.of(Permissions.CREATED,Permissions.READ,Permissions.UPDATE,Permissions.DELETE)),
    USER(Set.of(Permissions.READ,Permissions.CREATED));


    private final Set<Permissions> permisos;


    Role(Set<Permissions> permisos) {
        this.permisos = permisos;
    }
    public Set<Permissions> getPermisos() {
        return permisos;
    }

    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}