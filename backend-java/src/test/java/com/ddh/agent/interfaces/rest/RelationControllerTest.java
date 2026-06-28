package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("reluser_" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    private Long importTable(String filename, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", filename, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/tables/import")
                .file(file).param("scope", "2").header("Authorization", token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private long columnId(Long tableId, String columnName) throws Exception {
        String body = mvc.perform(get("/api/tables/" + tableId).header("Authorization", token))
            .andReturn().getResponse().getContentAsString();
        for (JsonNode c : mapper.readTree(body).get("columns")) {
            if (columnName.equals(c.get("column_name").asText())) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException("column not found: " + columnName);
    }

    @Test
    void crudListAndGraph() throws Exception {
        Long orders = importTable("orders.csv",
            "column_name,data_type,comment\nuser_id,BIGINT,用户ID\namount,DECIMAL,金额\n");
        Long users = importTable("users.csv",
            "column_name,data_type,comment\nid,BIGINT,主键\nname,VARCHAR,姓名\n");
        long ordersUserId = columnId(orders, "user_id");
        long usersId = columnId(users, "id");

        String payload = "{"
            + "\"source_table_id\":" + orders + ","
            + "\"target_table_id\":" + users + ","
            + "\"relation_type\":\"MANY_TO_ONE\","
            + "\"description\":\"每个订单属于一个用户\","
            + "\"column_pairs\":[{\"source_column_id\":" + ordersUserId
            + ",\"target_column_id\":" + usersId + "}]"
            + "}";

        String created = mvc.perform(post("/api/relations")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long relId = mapper.readTree(created).get("id").asLong();

        mvc.perform(get("/api/relations").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].source_table_name").value("orders"))
            .andExpect(jsonPath("$[0].target_table_name").value("users"))
            .andExpect(jsonPath("$[0].relation_type").value("MANY_TO_ONE"))
            .andExpect(jsonPath("$[0].column_pairs[0].source_column_name").value("user_id"))
            .andExpect(jsonPath("$[0].column_pairs[0].target_column_name").value("id"));

        String graphBody = "{\"tableIds\":[" + orders + "," + users + "]}";
        mvc.perform(post("/api/relations/graph")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(graphBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes.length()").value(2))
            .andExpect(jsonPath("$.edges.length()").value(1))
            .andExpect(jsonPath("$.edges[0].relation_type").value("MANY_TO_ONE"))
            .andExpect(jsonPath("$.edges[0].column_pairs[0].source_column_name").value("user_id"));

        mvc.perform(delete("/api/relations/" + relId).header("Authorization", token))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/relations").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createWithColumnNotInTable_returns400() throws Exception {
        Long orders = importTable("o2.csv", "column_name,data_type\nuser_id,BIGINT\n");
        Long users = importTable("u2.csv", "column_name,data_type\nid,BIGINT\n");

        String payload = "{"
            + "\"source_table_id\":" + orders + ","
            + "\"target_table_id\":" + users + ","
            + "\"relation_type\":\"MANY_TO_ONE\","
            + "\"column_pairs\":[{\"source_column_id\":999999,\"target_column_id\":999999}]"
            + "}";

        mvc.perform(post("/api/relations")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isBadRequest());
    }
}
