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

    private static final String HEADER =
        "字段序号,字段名称,字段中文名,字段类型,字段长度,字段精度,是否分布键,是否分区建,是否主键,是否可为空,代码信息,缺省值,下游作业数\n";

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
            HEADER + "1,user_id,用户ID,BIGINT\n2,amount,金额,DECIMAL\n");
        Long users = importTable("users.csv",
            HEADER + "1,id,主键,BIGINT\n2,name,姓名,VARCHAR\n");
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
        Long orders = importTable("o2.csv", HEADER + "1,user_id,,BIGINT\n");
        Long users = importTable("u2.csv", HEADER + "1,id,,BIGINT\n");

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
