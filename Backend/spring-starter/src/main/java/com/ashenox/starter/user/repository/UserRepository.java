package com.ashenox.starter.user.repository;





import com.ashenox.starter.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User>findByEmail(String email);
    User findByEmailIgnoreCase(String email);

}
