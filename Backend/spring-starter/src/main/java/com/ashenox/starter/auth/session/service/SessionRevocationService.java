package com.ashenox.starter.auth.session.service;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final AuthSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeInNewTransaction(String sessionId) {
        sessionRepository.revokeById(sessionId, Instant.now());
    }
}
