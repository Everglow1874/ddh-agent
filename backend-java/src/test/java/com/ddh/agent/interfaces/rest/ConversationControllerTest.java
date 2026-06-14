package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.project.Project;
import com.ddh.agent.domain.model.project.ProjectRepository;
import com.ddh.agent.domain.model.user.*;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;
    private Long projectId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("convuser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());

        Project project = new Project();
        project.setName("TestProject");
        project.setOwnerId(user.getId());
        project.setStatus(1);
        project.setCreatedAt(LocalDateTime.now());
        projectRepository.save(project);
        projectId = project.getId();
    }

    @Test
    void createConversation_chat_thenConfirmSchema() throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/" + projectId + "/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"table_ids\":[]}")
                .header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value(1))
            .andExpect(jsonPath("$.table_ids").isArray())
            .andReturn();

        Long convId = mapper.readTree(
            result.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/projects/" + projectId + "/conversations")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(convId));

        mvc.perform(post("/api/conversations/" + convId + "/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(get("/api/conversations/" + convId + "/messages")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].role").value("user"));

        mvc.perform(post("/api/conversations/" + convId + "/confirm-schema")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_table\":\"dim_user\",\"columns\":[{\"name\":\"uid\",\"type\":\"bigint\"}]}")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value(3));
    }
}