# DDH Agent Java 迁移 — Plan B：Auth / Table / Project / Conversation CRUD

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**前置条件：** Plan A 已完成（实体、Mapper、Repository 实现、JWT 安全层均就绪）。

**Goal:** 实现 Auth、Table、Project、Conversation 四个模块的全部 REST 接口（不含 SSE 流式推送），让前端的登录、项目管理、数据表管理、对话 CRUD 全部可用。

**Architecture:** DDD 四层；本计划实现 interfaces（Controller）和 application（AppService + Assembler）两层。

**Tech Stack:** Spring Boot 2.7, MyBatis Plus, Spring Security BCrypt, Jackson SNAKE_CASE, MockMvc 测试

---

## 文件清单

```
backend-java/src/main/java/com/ddh/agent/
├── interfaces/
│   ├── rest/
│   │   ├── AuthController.java
│   │   ├── TableController.java
│   │   ├── ProjectController.java
│   │   └── ConversationController.java        # 仅 CRUD，/stream 留给 Plan C
│   └── dto/
│       ├── request/
│       │   ├── RegisterRequest.java
│       │   ├── LoginRequest.java
│       │   ├── TableUpdateRequest.java
│       │   ├── ProjectCreateRequest.java
│       │   ├── ProjectUpdateRequest.java
│       │   ├── TableAssociateRequest.java
│       │   ├── CreateConversationRequest.java
│       │   ├── SetConversationTablesRequest.java
│       │   ├── ChatRequest.java
│       │   ├── ConfirmSchemaRequest.java
│       │   └── ConfirmStepsRequest.java
│       └── response/
│           ├── TokenResponse.java
│           ├── UserResponse.java
│           ├── TableResponse.java
│           ├── TableDetailResponse.java
│           ├── ColumnResponse.java
│           ├── ProjectResponse.java
│           ├── ConversationResponse.java
│           └── MessageResponse.java
├── application/
│   ├── service/
│   │   ├── AuthAppService.java
│   │   ├── TableAppService.java
│   │   ├── ProjectAppService.java
│   │   └── ConversationAppService.java
│   └── assembler/
│       ├── TableAssembler.java
│       ├── ProjectAssembler.java
│       └── ConversationAssembler.java
└── infrastructure/
    └── config/
        └── AppProperties.java                 # @ConfigurationProperties 绑定 yml
```

---

## Task 9：公共 DTO + AppProperties

**Files:**
- Create: `infrastructure/config/AppProperties.java`
- Create: 所有 request/response DTO

- [ ] **Step 1: 创建 `AppProperties.java`**

```java
package com.ddh.agent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "files")
public class AppProperties {
    private String projectsDir = "./projects";

    public String getProjectsDir() { return projectsDir; }
    public void setProjectsDir(String projectsDir) { this.projectsDir = projectsDir; }
}
```

- [ ] **Step 2: 创建 Request DTO**

`RegisterRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
}
```

`LoginRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class LoginRequest {
    private String username;
    private String password;
}
```

`TableUpdateRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class TableUpdateRequest {
    private String name;
    private String description;
}
```

`ProjectCreateRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class ProjectCreateRequest {
    private String name;
    private String description;
}
```

`ProjectUpdateRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class ProjectUpdateRequest {
    private String name;
    private String description;
}
```

`TableAssociateRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
import java.util.List;
@Data
public class TableAssociateRequest {
    private List<Long> tableIds;
}
```

`CreateConversationRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
import java.util.Collections;
import java.util.List;
@Data
public class CreateConversationRequest {
    private List<Long> tableIds = Collections.emptyList();
}
```

`SetConversationTablesRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
import java.util.List;
@Data
public class SetConversationTablesRequest {
    private List<Long> tableIds;
}
```

`ChatRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
@Data
public class ChatRequest {
    private String message;
}
```

`ConfirmSchemaRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class ConfirmSchemaRequest {
    private String targetTable;
    private List<Map<String, Object>> columns;
}
```

`ConfirmStepsRequest.java`
```java
package com.ddh.agent.interfaces.dto.request;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class ConfirmStepsRequest {
    private List<Map<String, Object>> steps;
}
```

- [ ] **Step 3: 创建 Response DTO**

`TokenResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String tokenType;
}
```

`UserResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private Integer role;
}
```

`ColumnResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
@Data
public class ColumnResponse {
    private Long id;
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
}
```

`TableResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TableResponse {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private LocalDateTime createdAt;
}
```

`TableDetailResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
import java.util.List;
@Data
public class TableDetailResponse {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private List<ColumnResponse> columns;
}
```

`ProjectResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private Integer status;
    private LocalDateTime createdAt;
}
```

`ConversationResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class ConversationResponse {
    private Long id;
    private Long projectId;
    private Integer state;
    private LocalDateTime createdAt;
    private List<Long> tableIds;
}
```

`MessageResponse.java`
```java
package com.ddh.agent.interfaces.dto.response;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 编译确认**

```powershell
cd backend-java && mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add all request/response DTOs and AppProperties"
```

---

## Task 10：Auth 模块

**Files:**
- Create: `application/service/AuthAppService.java`
- Create: `interfaces/rest/AuthController.java`
- Create: `src/test/java/.../rest/AuthControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ddh/agent/interfaces/rest/AuthControllerTest.java
package com.ddh.agent.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void register_thenLogin_returnsToken() throws Exception {
        // register
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "username", "testuser",
                    "email", "test@example.com",
                    "password", "secret123"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("testuser"));

        // login
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "username", "testuser",
                    "password", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("bearer"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "username", "nobody",
                    "password", "wrong"))))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
mvn test -pl backend-java -Dtest=AuthControllerTest -q 2>&1 | Select-String "FAIL|ERROR|Tests run"
```
Expected: 失败（Controller 不存在）

- [ ] **Step 3: 创建 `AuthAppService.java`**

```java
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
```

- [ ] **Step 4: 创建 `AuthController.java`**

```java
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
```

- [ ] **Step 5: 运行确认通过**

```powershell
mvn test -pl backend-java -Dtest=AuthControllerTest -q
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add Auth module (register/login/me)"
```

---

## Task 11：Table 模块

**Files:**
- Create: `application/service/TableAppService.java`
- Create: `application/assembler/TableAssembler.java`
- Create: `interfaces/rest/TableController.java`
- Create: `src/test/java/.../rest/TableControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ddh/agent/interfaces/rest/TableControllerTest.java
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
        user.setUsername("tableuser_" + System.currentTimeMillis());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void importCsv_thenList() throws Exception {
        String csv = "column_name,data_type,comment\nuser_id,BIGINT,用户ID\nname,VARCHAR,姓名\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "users.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/tables/import")
                .file(file)
                .param("scope", "2")
                .header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("users"));

        mvc.perform(get("/api/tables")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("users"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
mvn test -pl backend-java -Dtest=TableControllerTest -q 2>&1 | Select-String "FAIL|ERROR|Tests run"
```

- [ ] **Step 3: 创建 `TableAssembler.java`**

```java
package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TableAssembler {

    public TableResponse toResponse(SourceTable t) {
        TableResponse r = new TableResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    public TableDetailResponse toDetailResponse(TableWithColumns t) {
        TableDetailResponse r = new TableDetailResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setColumns(t.getColumns().stream().map(this::toColumnResponse)
            .collect(Collectors.toList()));
        return r;
    }

    public ColumnResponse toColumnResponse(TableColumn c) {
        ColumnResponse r = new ColumnResponse();
        r.setId(c.getId());
        r.setColumnName(c.getColumnName());
        r.setDataType(c.getDataType());
        r.setComment(c.getComment());
        r.setSortOrder(c.getSortOrder());
        return r;
    }

    public TableDetailResponse toDetailResponseFromTable(SourceTable t, List<TableColumn> cols) {
        TableDetailResponse r = new TableDetailResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setColumns(cols.stream().map(this::toColumnResponse).collect(Collectors.toList()));
        return r;
    }
}
```

- [ ] **Step 4: 创建 `TableAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.TableUpdateRequest;
import com.ddh.agent.interfaces.dto.response.*;
import com.ddh.agent.application.assembler.TableAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TableAppService {

    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private TableAssembler assembler;

    public TableResponse importCsv(MultipartFile file, Integer scope,
                                   String description, Long currentUserId) {
        String filename = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "unknown.csv";
        String tableName = filename.contains(".")
            ? filename.substring(0, filename.lastIndexOf('.')) : filename;

        List<TableColumn> columns;
        try {
            columns = parseCsv(file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file");
        }

        SourceTable table = new SourceTable();
        table.setName(tableName);
        table.setDescription(description);
        table.setScope(scope);
        table.setOwnerId(scope == 1 ? null : currentUserId);
        table.setCreatedAt(LocalDateTime.now());
        sourceTableRepository.save(table);

        int i = 0;
        for (TableColumn col : columns) {
            col.setTableId(table.getId());
            col.setSortOrder(i++);
        }
        sourceTableRepository.saveColumns(columns);
        return assembler.toResponse(table);
    }

    /** CSV 需含 column_name、data_type 列，comment 可选 */
    private List<TableColumn> parseCsv(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }
            List<String> headers = Arrays.stream(headerLine.split(","))
                .map(String::trim).collect(Collectors.toList());
            int colNameIdx = headers.indexOf("column_name");
            int dataTypeIdx = headers.indexOf("data_type");
            int commentIdx = headers.indexOf("comment");
            if (colNameIdx < 0 || dataTypeIdx < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CSV missing required columns: column_name, data_type");
            }
            List<TableColumn> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                TableColumn col = new TableColumn();
                col.setColumnName(parts[colNameIdx].trim());
                col.setDataType(parts[dataTypeIdx].trim());
                if (commentIdx >= 0 && commentIdx < parts.length) {
                    String c = parts[commentIdx].trim();
                    col.setComment(c.isEmpty() ? null : c);
                }
                result.add(col);
            }
            return result;
        }
    }

    public List<TableResponse> listTables(String scope, Long currentUserId) {
        return sourceTableRepository.findVisible(currentUserId).stream()
            .filter(t -> {
                if ("public".equals(scope)) return t.getScope() == 1;
                if ("private".equals(scope)) return t.getScope() == 2
                    && currentUserId.equals(t.getOwnerId());
                return true;
            })
            .map(assembler::toResponse)
            .collect(Collectors.toList());
    }

    public TableDetailResponse getTable(Long tableId) {
        SourceTable table = sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        List<TableColumn> cols = sourceTableRepository.findColumnsByTableId(tableId);
        return assembler.toDetailResponseFromTable(table, cols);
    }

    public TableResponse updateTable(Long tableId, TableUpdateRequest req) {
        SourceTable table = sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        if (req.getName() != null) table.setName(req.getName());
        if (req.getDescription() != null) table.setDescription(req.getDescription());
        sourceTableRepository.save(table);
        return assembler.toResponse(table);
    }

    public void deleteTable(Long tableId) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        sourceTableRepository.deleteById(tableId);
    }
}
```

- [ ] **Step 5: 创建 `TableController.java`**

```java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.TableAppService;
import com.ddh.agent.interfaces.dto.request.TableUpdateRequest;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    @Autowired private TableAppService tableAppService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse importTable(@RequestParam("file") MultipartFile file,
                                     @RequestParam("scope") Integer scope,
                                     @RequestParam(value = "description", required = false) String description,
                                     Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return tableAppService.importCsv(file, scope, description, userId);
    }

    @GetMapping
    public List<TableResponse> listTables(
            @RequestParam(required = false) String scope,
            Authentication auth) {
        return tableAppService.listTables(scope, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{tableId}")
    public TableDetailResponse getTable(@PathVariable Long tableId) {
        return tableAppService.getTable(tableId);
    }

    @PutMapping("/{tableId}")
    public TableResponse updateTable(@PathVariable Long tableId,
                                     @RequestBody TableUpdateRequest req) {
        return tableAppService.updateTable(tableId, req);
    }

    @DeleteMapping("/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTable(@PathVariable Long tableId) {
        tableAppService.deleteTable(tableId);
    }
}
```

- [ ] **Step 6: 运行确认通过**

```powershell
mvn test -pl backend-java -Dtest=TableControllerTest -q
```
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add Table module (import CSV, list, get, update, delete)"
```

---

## Task 12：Project 模块

**Files:**
- Create: `application/service/ProjectAppService.java`
- Create: `application/assembler/ProjectAssembler.java`
- Create: `interfaces/rest/ProjectController.java`
- Create: `src/test/java/.../rest/ProjectControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ddh/agent/interfaces/rest/ProjectControllerTest.java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.domain.model.user.*;
import com.ddh.agent.infrastructure.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("projuser_" + System.currentTimeMillis());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode("pass"));
        user.setRole(2);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        token = "Bearer " + jwtUtil.generateToken(user.getId());
    }

    @Test
    void createAndGetProject() throws Exception {
        MvcResult result = mvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "MyProj", "description", "desc")))
                .header("Authorization", token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("MyProj"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        Long projectId = mapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/projects")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(projectId));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
mvn test -pl backend-java -Dtest=ProjectControllerTest -q 2>&1 | Select-String "FAIL|ERROR|Tests run"
```

- [ ] **Step 3: 创建 `ProjectAssembler.java`**

```java
package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.project.Project;
import com.ddh.agent.interfaces.dto.response.ProjectResponse;
import org.springframework.stereotype.Component;

@Component
public class ProjectAssembler {
    public ProjectResponse toResponse(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setOwnerId(p.getOwnerId());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }
}
```

- [ ] **Step 4: 创建 `ProjectAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.application.assembler.ProjectAssembler;
import com.ddh.agent.application.assembler.TableAssembler;
import com.ddh.agent.domain.model.project.*;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectAppService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private ProjectAssembler projectAssembler;
    @Autowired private TableAssembler tableAssembler;

    public List<ProjectResponse> listProjects(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream()
            .map(projectAssembler::toResponse).collect(Collectors.toList());
    }

    public ProjectResponse createProject(ProjectCreateRequest req, Long ownerId) {
        Project p = new Project();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setOwnerId(ownerId);
        p.setStatus(1);
        p.setCreatedAt(LocalDateTime.now());
        projectRepository.save(p);
        return projectAssembler.toResponse(p);
    }

    public ProjectResponse updateProject(Long id, ProjectUpdateRequest req, Long ownerId) {
        Project p = getOwnedProject(id, ownerId);
        if (req.getName() != null) p.setName(req.getName());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        projectRepository.save(p);
        return projectAssembler.toResponse(p);
    }

    public void deleteProject(Long id, Long ownerId) {
        getOwnedProject(id, ownerId);
        projectRepository.deleteById(id);
    }

    public List<TableResponse> getProjectTables(Long projectId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        List<ProjectTable> rows = projectRepository.findTablesByProjectId(projectId);
        return rows.stream()
            .map(pt -> sourceTableRepository.findById(pt.getTableId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(tableAssembler::toResponse)
            .collect(Collectors.toList());
    }

    public Map<String, Object> associateTables(Long projectId,
                                               TableAssociateRequest req,
                                               Long ownerId) {
        getOwnedProject(projectId, ownerId);
        List<Long> added = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        for (Long tableId : req.getTableIds()) {
            if (projectRepository.existsProjectTable(projectId, tableId)) {
                skipped.add(tableId);
            } else {
                projectRepository.addProjectTable(projectId, tableId);
                added.add(tableId);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("skipped", skipped);
        return result;
    }

    public void removeTable(Long projectId, Long tableId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        if (!projectRepository.existsProjectTable(projectId, tableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Association not found");
        }
        projectRepository.removeProjectTable(projectId, tableId);
    }

    public List<TableDetailResponse> getProjectTablesWithDetails(Long projectId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        return sourceTableRepository.findWithColumnsByProjectId(projectId).stream()
            .map(tableAssembler::toDetailResponse)
            .collect(Collectors.toList());
    }

    private Project getOwnedProject(Long id, Long ownerId) {
        Project p = projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project not found"));
        if (!p.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return p;
    }
}
```

- [ ] **Step 5: 创建 `ProjectController.java`**

```java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.ProjectAppService;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired private ProjectAppService projectAppService;

    @GetMapping
    public List<ProjectResponse> list(Authentication auth) {
        return projectAppService.listProjects(Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@RequestBody ProjectCreateRequest req, Authentication auth) {
        return projectAppService.createProject(req, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id,
                                  @RequestBody ProjectUpdateRequest req,
                                  Authentication auth) {
        return projectAppService.updateProject(id, req, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        projectAppService.deleteProject(id, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{id}/tables")
    public List<TableResponse> getTables(@PathVariable Long id, Authentication auth) {
        return projectAppService.getProjectTables(id, Long.valueOf(auth.getName()));
    }

    @PostMapping("/{id}/tables")
    public Map<String, Object> associateTables(@PathVariable Long id,
                                               @RequestBody TableAssociateRequest req,
                                               Authentication auth) {
        return projectAppService.associateTables(id, req, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}/tables/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTable(@PathVariable Long id,
                            @PathVariable Long tableId,
                            Authentication auth) {
        projectAppService.removeTable(id, tableId, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{id}/tables-with-details")
    public List<TableDetailResponse> tablesWithDetails(@PathVariable Long id,
                                                       Authentication auth) {
        return projectAppService.getProjectTablesWithDetails(id, Long.valueOf(auth.getName()));
    }
}
```

- [ ] **Step 6: 运行确认通过**

```powershell
mvn test -pl backend-java -Dtest=ProjectControllerTest -q
```
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add Project module (CRUD, table association, tables-with-details)"
```

---

## Task 13：Conversation CRUD 模块

**Files:**
- Create: `application/service/ConversationAppService.java`
- Create: `application/assembler/ConversationAssembler.java`
- Create: `interfaces/rest/ConversationController.java`（含 /chat POST 和 /confirm-schema /confirm-steps；**/stream 留给 Plan C**）
- Create: `src/test/java/.../rest/ConversationControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ddh/agent/interfaces/rest/ConversationControllerTest.java
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
import java.util.*;

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
        user.setUsername("convuser_" + System.currentTimeMillis());
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
    void createConversation_thenList() throws Exception {
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
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
mvn test -pl backend-java -Dtest=ConversationControllerTest -q 2>&1 | Select-String "FAIL|ERROR|Tests run"
```

- [ ] **Step 3: 创建 `ConversationAssembler.java`**

```java
package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ConversationAssembler {

    public ConversationResponse toResponse(Conversation c, List<Long> tableIds) {
        ConversationResponse r = new ConversationResponse();
        r.setId(c.getId());
        r.setProjectId(c.getProjectId());
        r.setState(c.getState());
        r.setCreatedAt(c.getCreatedAt());
        r.setTableIds(tableIds);
        return r;
    }

    public MessageResponse toMessageResponse(Message m) {
        MessageResponse r = new MessageResponse();
        r.setId(m.getId());
        r.setConversationId(m.getConversationId());
        r.setRole(m.getRole());
        r.setContent(m.getContent());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }
}
```

- [ ] **Step 4: 创建 `ConversationAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.application.assembler.ConversationAssembler;
import com.ddh.agent.application.assembler.TableAssembler;
import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.domain.model.project.ProjectRepository;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationAppService {

    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private ConversationAssembler assembler;
    @Autowired private TableAssembler tableAssembler;
    @Autowired private ObjectMapper objectMapper;

    public ConversationResponse createConversation(Long projectId,
                                                   CreateConversationRequest req,
                                                   Long ownerId) {
        projectRepository.findById(projectId)
            .filter(p -> p.getOwnerId().equals(ownerId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project not found"));

        Conversation conv = new Conversation();
        conv.setProjectId(projectId);
        conv.setState(1);
        conv.setCreatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        List<Long> tableIds = req.getTableIds() != null ? req.getTableIds() : List.of();
        if (!tableIds.isEmpty()) {
            conversationRepository.replaceConversationTables(conv.getId(), tableIds);
        }
        return assembler.toResponse(conv, tableIds);
    }

    public List<ConversationResponse> listConversations(Long projectId, Long ownerId) {
        projectRepository.findById(projectId)
            .filter(p -> p.getOwnerId().equals(ownerId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project not found"));

        return conversationRepository.findByProjectId(projectId).stream()
            .map(c -> {
                List<Long> tids = conversationRepository
                    .findTablesByConversationId(c.getId()).stream()
                    .map(ConversationTable::getTableId).collect(Collectors.toList());
                return assembler.toResponse(c, tids);
            }).collect(Collectors.toList());
    }

    public List<MessageResponse> getMessages(Long convId) {
        requireConversation(convId);
        return conversationRepository.findMessagesByConversationId(convId).stream()
            .map(assembler::toMessageResponse).collect(Collectors.toList());
    }

    public List<TableDetailResponse> getConversationTables(Long convId) {
        requireConversation(convId);
        return sourceTableRepository.findWithColumnsByConversationId(convId).stream()
            .map(tableAssembler::toDetailResponse).collect(Collectors.toList());
    }

    public ConversationResponse setConversationTables(Long convId,
                                                      SetConversationTablesRequest req) {
        Conversation conv = requireConversation(convId);
        conversationRepository.replaceConversationTables(convId, req.getTableIds());
        return assembler.toResponse(conv, req.getTableIds());
    }

    public Map<String, Object> saveUserMessage(Long convId, ChatRequest req) {
        requireConversation(convId);
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setRole("user");
        msg.setContent(req.getMessage());
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "ok");
        result.put("conversation_id", convId);
        return result;
    }

    public ConversationResponse confirmSchema(Long convId, ConfirmSchemaRequest req) {
        Conversation conv = requireConversation(convId);
        if (conv.getState() != 1 && conv.getState() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot confirm schema in state " + conv.getState());
        }
        conv.setState(3);
        conversationRepository.save(conv);

        String colJson;
        try {
            colJson = objectMapper.writeValueAsString(req.getColumns());
        } catch (JsonProcessingException e) {
            colJson = "[]";
        }
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setRole("user");
        msg.setContent("目标表结构已确认。目标表：" + req.getTargetTable() + "，字段：" + colJson + "。请规划ETL步骤。");
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);

        List<Long> tids = conversationRepository.findTablesByConversationId(convId)
            .stream().map(ConversationTable::getTableId).collect(Collectors.toList());
        return assembler.toResponse(conv, tids);
    }

    public ConversationResponse confirmSteps(Long convId, ConfirmStepsRequest req) {
        Conversation conv = requireConversation(convId);
        if (conv.getState() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot confirm steps in state " + conv.getState());
        }
        conv.setState(4);
        conversationRepository.save(conv);

        String stepsJson;
        try {
            stepsJson = objectMapper.writeValueAsString(req.getSteps());
        } catch (JsonProcessingException e) {
            stepsJson = "[]";
        }
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setRole("user");
        msg.setContent("ETL步骤已确认。步骤计划：" + stepsJson + "。请为每个步骤生成GaussDB SQL。");
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);

        List<Long> tids = conversationRepository.findTablesByConversationId(convId)
            .stream().map(ConversationTable::getTableId).collect(Collectors.toList());
        return assembler.toResponse(conv, tids);
    }

    public Conversation requireConversation(Long convId) {
        return conversationRepository.findById(convId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Conversation not found"));
    }
}
```

- [ ] **Step 5: 创建 `ConversationController.java`（不含 /stream，由 Plan C 补充）**

```java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.ConversationAppService;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConversationController {

    @Autowired private ConversationAppService conversationAppService;

    @PostMapping("/projects/{projectId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@PathVariable Long projectId,
                                       @RequestBody CreateConversationRequest req,
                                       Authentication auth) {
        return conversationAppService.createConversation(
            projectId, req, Long.valueOf(auth.getName()));
    }

    @GetMapping("/projects/{projectId}/conversations")
    public List<ConversationResponse> list(@PathVariable Long projectId,
                                           Authentication auth) {
        return conversationAppService.listConversations(
            projectId, Long.valueOf(auth.getName()));
    }

    @PostMapping("/conversations/{convId}/chat")
    public Map<String, Object> chat(@PathVariable Long convId,
                                    @RequestBody ChatRequest req,
                                    Authentication auth) {
        return conversationAppService.saveUserMessage(convId, req);
    }

    @PostMapping("/conversations/{convId}/confirm-schema")
    public ConversationResponse confirmSchema(@PathVariable Long convId,
                                              @RequestBody ConfirmSchemaRequest req,
                                              Authentication auth) {
        return conversationAppService.confirmSchema(convId, req);
    }

    @PostMapping("/conversations/{convId}/confirm-steps")
    public ConversationResponse confirmSteps(@PathVariable Long convId,
                                             @RequestBody ConfirmStepsRequest req,
                                             Authentication auth) {
        return conversationAppService.confirmSteps(convId, req);
    }

    @GetMapping("/conversations/{convId}/messages")
    public List<MessageResponse> messages(@PathVariable Long convId,
                                          Authentication auth) {
        return conversationAppService.getMessages(convId);
    }

    @GetMapping("/conversations/{convId}/tables")
    public List<TableDetailResponse> convTables(@PathVariable Long convId,
                                                Authentication auth) {
        return conversationAppService.getConversationTables(convId);
    }

    @PutMapping("/conversations/{convId}/tables")
    public ConversationResponse setTables(@PathVariable Long convId,
                                          @RequestBody SetConversationTablesRequest req,
                                          Authentication auth) {
        return conversationAppService.setConversationTables(convId, req);
    }

    // GET /conversations/{convId}/stream 由 Plan C 的 AgentAppService 注入后补充
}
```

- [ ] **Step 6: 运行确认通过**

```powershell
mvn test -pl backend-java -Dtest=ConversationControllerTest -q
```
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 7: 运行全部测试**

```powershell
mvn test -pl backend-java -q
```
Expected: `Tests run: 5+, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add Conversation CRUD (create, list, chat-save, confirm, messages, tables)"
```

---

**Plan B 完成。** 此时所有 CRUD REST 接口均可用，前端登录、项目管理、数据表管理、对话管理全部正常。继续执行 [Plan C — LLM 适配器 + Agent 状态机 + SSE 流式](2026-06-13-java-plan-c-agent.md)。
