package com.ashenox.starter.auth.session.repository;

import com.ashenox.starter.auth.session.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findWithUserById(String id);
}
