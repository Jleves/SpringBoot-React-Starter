package com.ashenox.starter.auth.session.repository;

import com.ashenox.starter.auth.session.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findWithUserById(String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession session
               set session.revokedAt = :revokedAt,
                   session.version = session.version + 1
             where session.id = :sessionId
               and session.revokedAt is null
            """)
    int revokeById(@Param("sessionId") String sessionId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession session
               set session.revokedAt = :revokedAt,
                   session.version = session.version + 1
             where session.user.id = :userId
               and session.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
