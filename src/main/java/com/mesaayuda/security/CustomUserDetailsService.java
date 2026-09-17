package com.mesaayuda.security;

import com.mesaayuda.entity.User;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String corporateEmail) throws UsernameNotFoundException {
        User user = userRepository.findByCorporateEmailUser(corporateEmail)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + corporateEmail));

        if (!"ACTIVE".equals(user.getStatusUser())) {
            throw new UsernameNotFoundException("Usuario inactivo: " + corporateEmail);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getCorporateEmailUser(),
                user.getPasswordUser(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRolUser().getNameRole()))
        );
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String email) {
        return userRepository.findByCorporateEmailUser(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "correo", email));
    }
}