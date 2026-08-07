package com.ashenox.starter.user.service;

import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserDTO> listAll();

    void updateUser(User user);

    void deleteUser(Long id);
}
