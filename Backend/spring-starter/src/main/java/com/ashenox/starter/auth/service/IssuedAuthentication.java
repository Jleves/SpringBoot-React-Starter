package com.ashenox.starter.auth.service;

import com.ashenox.starter.user.dto.UserDTO;

public record IssuedAuthentication(
        UserDTO user,
        String accessToken,
        String refreshToken,
        String sessionId
) {
}
