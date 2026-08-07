package com.ashenox.starter.security.service;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(EmailNormalizer.normalize(email))
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
