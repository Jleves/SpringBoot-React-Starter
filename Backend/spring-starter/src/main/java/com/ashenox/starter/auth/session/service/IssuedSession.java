package com.ashenox.starter.auth.session.service;

import com.ashenox.starter.auth.session.model.AuthSession;

public record IssuedSession(AuthSession session, String refreshToken) {
}
