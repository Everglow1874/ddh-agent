package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TableControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("tableuser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void importCsv_thenGetDetail() throws Exception {
        String csv = "column_name,data_type,comment\nuser_id,BIGINT,用户ID\nname,VARCHAR,姓名\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "users.csv", "text/csv", csv.getBytes());

        String body = mvc.perform(multipart("/api/tables/import")
                .file(file)
                .param("scope", "2")
                .header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("users"))
            .andReturn().getResponse().getContentAsString();

        Long tableId = mapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/tables/" + tableId)
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns.length()").value(2))
            .andExpect(jsonPath("$.columns[0].column_name").value("user_id"));

        mvc.perform(get("/api/tables")
                .header("Authorization", token))
            .andExpect(status().isOk());
    }

    @Test
    void importCsv_missingColumns_returns400() throws Exception {
        String badCsv = "col,type\nid,bigint\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad.csv", "text/csv", badCsv.getBytes());

        mvc.perform(multipart("/api/tables/import")
                .file(file)
                .param("scope", "2")
                .header("Authorization", token))
            .andExpect(status().isBadRequest());
    }
}