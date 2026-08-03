package com.ashenox.starter.user.service.Interface;






import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

   // User saveUser (User user);
   // Boolean verifyToken(String token);

    void deleteUser( Long id);
    void updateUser(User user);
    List<UserDTO> listAll();


    Optional<User> findUsername(String username);

    Optional<User> findByid(Long id);
    Optional<User> findByEmail( String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
