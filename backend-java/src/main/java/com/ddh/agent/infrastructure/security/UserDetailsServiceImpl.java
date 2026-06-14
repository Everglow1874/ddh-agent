package com.ddh.agent.infrastructure.security;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.valueOf(userId))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return new org.springframework.security.core.userdetails.User(
            String.valueOf(user.getId()),
            user.getPasswordHash(),
            Collections.emptyList()
        );
    }
}
