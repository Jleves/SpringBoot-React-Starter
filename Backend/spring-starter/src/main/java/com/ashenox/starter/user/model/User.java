package com.ashenox.starter.user.model;


import com.ashenox.starter.shared.persistence.EntidadAuditable;
import com.ashenox.starter.user.Enum.Permissions;
import com.ashenox.starter.user.Enum.Role;
import jakarta.persistence.*;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends EntidadAuditable implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;
    //private String username; //fijarse donde tenemos problemas

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role rol;

    public String getUsername() { return email; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Rol
        authorities.add(new SimpleGrantedAuthority(getRol().getAuthority()));

        // Permisos
        for (Permissions permiso : getRol().getPermisos()) {
            authorities.add(new SimpleGrantedAuthority(permiso.getAuthority()));
        }

        return authorities;
    }

}