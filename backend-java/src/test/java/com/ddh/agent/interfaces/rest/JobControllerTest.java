package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.etl.EtlJob;
import com.ddh.agent.domain.model.etl.EtlRepository;
import com.ddh.agent.domain.model.etl.EtlStep;
import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.domain.service.EtlDomainService;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired EtlRepository etlRepository;
    @Autowired EtlDomainService etlDomainService;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("jobuser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void seedJob_thenGet_sql_download() throws Exception {
        EtlJob job = new EtlJob();
        job.setProjectId(1L);
        job.setTargetTable("dim_test");
        job.setTargetSchema("[{\"name\":\"id\",\"type\":\"bigint\"}]");
        job.setCreatedAt(LocalDateTime.now());
        String sqlPath = etlDomainService.writeSqlFile(1L, 1, "load", "SELECT 1;");
        EtlDomainService.PlanMdResult planResult = etlDomainService.writePlanMd(
            1L, "dim_test", "req", java.util.Collections.emptyList());
        job.setPlanMdPath(planResult.path);
        job.setPlanContent(planResult.content);
        etlRepository.saveJob(job);

        EtlStep step = etlDomainService.createStep(job.getId(), 1, "load", false, sqlPath, "SELECT 1;");

        mvc.perform(get("/api/projects/1/jobs").header("Authorization", token))
            .andExpect(status().isOk());

        mvc.perform(get("/api/jobs/" + job.getId()).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target_table").value("dim_test"))
            .andExpect(jsonPath("$.target_schema").isArray())
            .andExpect(jsonPath("$.target_schema.length()").value(1))
            .andExpect(jsonPath("$.steps.length()").value(1))
            .andExpect(jsonPath("$.steps[0].sql_file_path").exists());

        mvc.perform(get("/api/jobs/" + job.getId() + "/steps/" + step.getId() + "/sql")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.step_id").value(step.getId()))
            .andExpect(jsonPath("$.sql").value("SELECT 1;"));

        mvc.perform(get("/api/jobs/" + job.getId() + "/download")
                .header("Authorization", token))
            .andExpect(status().isOk());
    }
}
