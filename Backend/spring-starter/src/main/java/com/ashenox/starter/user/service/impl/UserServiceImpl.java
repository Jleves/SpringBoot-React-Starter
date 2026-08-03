package com.ashenox.starter.user.service.impl;




import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.Interface.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {
    private final UserRepository userRepository;
   // private final ConfirmationRepository confirmationRepository;
  //  private final EmailService emailServiceImple;




//Registro
//Verificacion cuando se registran
/*
    @Override
    public Boolean verifyToken(String token) {
        log.info("Inicio Confirmacion");
      //  Confirmation confirmation= confirmationRepository.findByToken(token);
        User user= userRepository.findByEmail();
        //.findByEmailIgnoreCase(confirmation.getUser().getEmail());
      //  user.setEnabled(true);
        log.info("Enabled User true");
        userRepository.save(user);
      //  confirmationRepository.delete(confirmation);

        log.info("Verification email ok");
        return Boolean.TRUE;
    }
*/



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {


        Optional<User> buscarUsuario = findByEmail(username);

        if(buscarUsuario.isPresent()){
            return buscarUsuario.get();

        }else throw new UsernameNotFoundException("No se encontro el usuario");

    }
    @Override
    public Optional<User> findUsername(String username){return  userRepository.findByUsername(username);}




    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }


    @Override
    public Optional<User/**UserDto**/> findByid(Long id) {
        Optional<User>findUser = userRepository.findById(id);
            return  findUser

                    /**findUser.map(UserDTO::fromUser)**/;

    }

    @Override
    public Optional<User> findByEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        return userOptional;
    }


    public Optional<UserDTO> findUserByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.map(UserDTO::fromUser);
    }


    @Override
    public void deleteUser(Long id) {

        Optional<User>findUser = userRepository.findById(id);
        if(findUser.isPresent()){
            userRepository.deleteById(id);
        }

    }

    @Override
    public void updateUser(User user) {
        userRepository.save(user);
    }

    @Override
    public List<UserDTO> listAll() {
        return userRepository.findAll().stream()
                .map(UserDTO::fromUser)
                .collect(Collectors.toList());
    }
}
