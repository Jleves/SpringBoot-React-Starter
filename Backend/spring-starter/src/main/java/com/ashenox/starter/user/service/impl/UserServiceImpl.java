package com.ashenox.starter.user.service.impl;

import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.UserService;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(EmailNormalizer.normalize(email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(EmailNormalizer.normalize(email));
    }

    @Override
    public List<UserDTO> listAll() {
        return userRepository.findAll().stream().map(UserDTO::fromUser).toList();
    }

    @Override
    public void updateUser(User user) {
        user.setEmail(EmailNormalizer.normalize(user.getEmail()));
        userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
