package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.AuthAppService;
import com.ddh.agent.interfaces.dto.request.LoginRequest;
import com.ddh.agent.interfaces.dto.request.RegisterRequest;
import com.ddh.agent.interfaces.dto.response.TokenResponse;
import com.ddh.agent.interfaces.dto.response.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthAppService authAppService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest req) {
        return authAppService.register(req);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest req) {
        return authAppService.login(req);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return authAppService.getCurrentUser(userId);
    }
}
