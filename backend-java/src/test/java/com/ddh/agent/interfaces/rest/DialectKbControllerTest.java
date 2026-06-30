package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.domain.service.DialectKbDomainService;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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
class DialectKbControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired DialectKbDomainService dialectKbDomainService;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("dialectuser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void typeRuleCrud_andPromptSection() throws Exception {
        String body = mvc.perform(post("/api/admin/dialect/types")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", token)
                .content("{\"type_name\":\"VARCHAR\",\"allowed_forms\":\"10,50,100,1000\","
                    + "\"rounding_rule\":\"长度向上取最近允许值\",\"enabled\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type_name").value("VARCHAR"))
            .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/admin/dialect/types").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        // 提示词段落应包含拼好的允许形态与取整规则
        String section = dialectKbDomainService.buildPromptSection();
        Assertions.assertTrue(section.contains("VARCHAR(10)/VARCHAR(50)/VARCHAR(100)/VARCHAR(1000)"), section);
        Assertions.assertTrue(section.contains("长度向上取最近允许值"));

        // 改为停用 → 提示词不再包含该规则
        mvc.perform(put("/api/admin/dialect/types/" + id)
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", token)
                .content("{\"type_name\":\"VARCHAR\",\"allowed_forms\":\"10,50,100,1000\",\"enabled\":0}"))
            .andExpect(status().isOk());
        Assertions.assertFalse(dialectKbDomainService.buildPromptSection().contains("VARCHAR(10)"));

        mvc.perform(delete("/api/admin/dialect/types/" + id).header("Authorization", token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/dialect/types").header("Authorization", token))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void functionRuleCrud_andPromptSection() throws Exception {
        String body = mvc.perform(post("/api/admin/dialect/functions")
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", token)
                .content("{\"function_name\":\"plat_to_date\",\"signature\":\"plat_to_date(text, fmt)\","
                    + "\"description\":\"按格式转日期\",\"example\":\"plat_to_date('20260101','YYYYMMDD')\",\"enabled\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.function_name").value("plat_to_date"))
            .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();

        String section = dialectKbDomainService.buildPromptSection();
        Assertions.assertTrue(section.contains("plat_to_date(text, fmt)"), section);
        Assertions.assertTrue(section.contains("按格式转日期"));
        Assertions.assertTrue(section.contains("平台内置函数"));

        // 清理，保证其它测试看到的是干净库
        mvc.perform(delete("/api/admin/dialect/functions/" + id).header("Authorization", token))
            .andExpect(status().isNoContent());
    }

    @Test
    void emptyKb_promptSectionIsBlank() {
        Assertions.assertEquals("", dialectKbDomainService.buildPromptSection());
    }

    @Test
    void updateMissing_returns404() throws Exception {
        mvc.perform(put("/api/admin/dialect/types/999999")
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", token)
                .content("{\"type_name\":\"X\"}"))
            .andExpect(status().isNotFound());
    }
}
