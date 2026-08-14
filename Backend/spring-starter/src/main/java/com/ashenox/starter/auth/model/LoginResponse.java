package com.ashenox.starter.auth.model;

import com.ashenox.starter.user.dto.UserDTO;

public record LoginResponse(UserDTO user) {
}
