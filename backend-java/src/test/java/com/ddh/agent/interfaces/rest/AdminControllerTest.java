package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("adminuser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(1);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void getConfig_returnsProviderAndModel() throws Exception {
        mvc.perform(get("/api/admin/config").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("claude"))
            .andExpect(jsonPath("$.model").value("claude-sonnet-4-6"));
    }

    @Test
    void updateConfig_switchesProvider() throws Exception {
        mvc.perform(put("/api/admin/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"deepseek\",\"model\":\"deepseek-chat\"}")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("deepseek"))
            .andExpect(jsonPath("$.model").value("deepseek-chat"));

        // restore to avoid bleeding into other tests
        mvc.perform(put("/api/admin/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"claude\",\"model\":\"claude-sonnet-4-6\"}")
                .header("Authorization", token))
            .andExpect(status().isOk());
    }

    @Test
    void updateConfig_invalidProvider_returns400() throws Exception {
        mvc.perform(put("/api/admin/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"gpt\",\"model\":\"x\"}")
                .header("Authorization", token))
            .andExpect(status().isBadRequest());
    }
}
