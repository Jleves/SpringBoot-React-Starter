package com.ashenox.starter.user.dto;

import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        Role role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
