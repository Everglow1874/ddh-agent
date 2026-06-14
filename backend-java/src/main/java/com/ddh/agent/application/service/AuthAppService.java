package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.ddh.agent.interfaces.dto.request.LoginRequest;
import com.ddh.agent.interfaces.dto.request.RegisterRequest;
import com.ddh.agent.interfaces.dto.response.TokenResponse;
import com.ddh.agent.interfaces.dto.response.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class AuthAppService {

    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    public UserResponse register(RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already taken");
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        return toResponse(user);
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new TokenResponse(jwtUtil.generateToken(user.getId()), "bearer");
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        return r;
    }
}
