package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

    /** 13 列标准表头 */
    private static final String HEADER =
        "字段序号,字段名称,字段中文名,字段类型,字段长度,字段精度,是否分布键,是否分区建,是否主键,是否可为空,代码信息,缺省值,下游作业数";

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
    void importCsvTemplate_persistsAll13Fields() throws Exception {
        // 第二行中文名含逗号，用引号包裹，检验引号感知解析
        String csv = HEADER + "\n"
            + "1,user_id,用户ID,VARCHAR,32,,是,否,是,否,,,0\n"
            + "2,amount,\"金额,元\",NUMERIC,18,2,否,否,否,是,CODE001,0,3\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String body = mvc.perform(multipart("/api/tables/import")
                .file(file).param("scope", "2").header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("users"))
            .andReturn().getResponse().getContentAsString();
        Long tableId = mapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/tables/" + tableId).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns.length()").value(2))
            // 第一列
            .andExpect(jsonPath("$.columns[0].column_name").value("user_id"))
            .andExpect(jsonPath("$.columns[0].comment").value("用户ID"))
            .andExpect(jsonPath("$.columns[0].col_length").value(32))
            .andExpect(jsonPath("$.columns[0].is_primary_key").value(1))
            .andExpect(jsonPath("$.columns[0].is_nullable").value(0))
            .andExpect(jsonPath("$.columns[0].is_distribution_key").value(1))
            // 第二列：含逗号中文名、精度、代码信息、缺省值、下游作业数
            .andExpect(jsonPath("$.columns[1].comment").value("金额,元"))
            .andExpect(jsonPath("$.columns[1].col_length").value(18))
            .andExpect(jsonPath("$.columns[1].col_precision").value(2))
            .andExpect(jsonPath("$.columns[1].is_nullable").value(1))
            .andExpect(jsonPath("$.columns[1].code_info").value("CODE001"))
            .andExpect(jsonPath("$.columns[1].default_value").value("0"))
            .andExpect(jsonPath("$.columns[1].downstream_job_count").value(3));
    }

    @Test
    void importXlsx_persistsAllFields() throws Exception {
        String[][] data = {
            HEADER.split(","),
            {"1", "order_id", "订单ID", "BIGINT", "20", "", "是", "否", "是", "否", "", "", "5"},
        };
        byte[] xlsx = buildXlsx(data);
        MockMultipartFile file = new MockMultipartFile(
            "file", "orders.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        String body = mvc.perform(multipart("/api/tables/import")
                .file(file).param("scope", "2").header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("orders"))
            .andReturn().getResponse().getContentAsString();
        Long tableId = mapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/tables/" + tableId).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns.length()").value(1))
            .andExpect(jsonPath("$.columns[0].column_name").value("order_id"))
            .andExpect(jsonPath("$.columns[0].comment").value("订单ID"))
            .andExpect(jsonPath("$.columns[0].col_length").value(20))
            .andExpect(jsonPath("$.columns[0].is_primary_key").value(1))
            .andExpect(jsonPath("$.columns[0].downstream_job_count").value(5));
    }

    @Test
    void import_missingRequiredHeader_returns400() throws Exception {
        String bad = "字段序号,字段中文名\n1,用户ID\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad.csv", "text/csv", bad.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/tables/import")
                .file(file).param("scope", "2").header("Authorization", token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void downloadTemplate_csv_containsHeaders() throws Exception {
        String content = mvc.perform(get("/api/tables/template").param("format", "csv")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("字段序号"));
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("下游作业数"));
    }

    @Test
    void downloadTemplate_xlsx_nonEmpty() throws Exception {
        byte[] body = mvc.perform(get("/api/tables/template").param("format", "xlsx")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(body.length > 0);
    }

    private byte[] buildXlsx(String[][] data) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
