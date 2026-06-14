# DDH Agent Java 迁移 — Plan D：ETL Job 查询 + Admin 配置 + 集成验收

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**前置条件：** Plan A、B、C 已完成。

**Goal:** 实现 ETL Job 查询接口（list/get/sql/download zip）、运行时 LLM 配置管理（Admin），通过完整集成验收测试（MockMvc），确认整个 Java 后端可替换 Python 后端。

**Architecture:** JobController / AdminController 均为标准 Spring MVC REST Controller，调用各自 AppService；集成测试使用 H2 内存数据库 + MockMvc，覆盖主要端点。

**Tech Stack:** Spring MVC, Spring Security, MyBatis Plus, MockMvc, H2, ZipOutputStream

---

## 文件清单

```
backend-java/src/main/java/com/ddh/agent/
├── application/service/
│   ├── JobAppService.java
│   └── AdminAppService.java
├── interfaces/rest/
│   ├── JobController.java
│   └── AdminController.java
└── application/dto/
    ├── JobResponse.java
    ├── StepResponse.java
    └── AdminConfigRequest.java

backend-java/src/test/java/com/ddh/agent/
├── AuthControllerTest.java
├── TableControllerTest.java
├── ConversationControllerTest.java
└── JobControllerTest.java
```

---

## Task 18：JobAppService + JobController

**Files:**
- Create: `application/dto/JobResponse.java`
- Create: `application/dto/StepResponse.java`
- Create: `application/service/JobAppService.java`
- Create: `interfaces/rest/JobController.java`

对应 Python `backend/app/routers/jobs.py` 的 4 个端点：list / get / sql（文本）/ download（zip）。

- [ ] **Step 1: 创建 `JobResponse.java` 和 `StepResponse.java`**

```java
// application/dto/JobResponse.java
package com.ddh.agent.application.dto;

import com.ddh.agent.domain.model.etl.EtlJob;
import java.time.LocalDateTime;
import java.util.List;

public class JobResponse {
    public Long id;
    public Long projectId;
    public String targetTable;
    public String targetSchema;
    public String planMdPath;
    public LocalDateTime createdAt;
    public List<StepResponse> steps;

    public static JobResponse from(EtlJob job, List<StepResponse> steps) {
        JobResponse r = new JobResponse();
        r.id = job.getId();
        r.projectId = job.getProjectId();
        r.targetTable = job.getTargetTable();
        r.targetSchema = job.getTargetSchema();
        r.planMdPath = job.getPlanMdPath();
        r.createdAt = job.getCreatedAt();
        r.steps = steps;
        return r;
    }
}
```

```java
// application/dto/StepResponse.java
package com.ddh.agent.application.dto;

import com.ddh.agent.domain.model.etl.EtlStep;

public class StepResponse {
    public Long id;
    public Long jobId;
    public Integer stepOrder;
    public String stepName;
    public Integer isTempTable;
    public String sqlFilePath;

    public static StepResponse from(EtlStep step) {
        StepResponse r = new StepResponse();
        r.id = step.getId();
        r.jobId = step.getJobId();
        r.stepOrder = step.getStepOrder();
        r.stepName = step.getStepName();
        r.isTempTable = step.getIsTempTable();
        r.sqlFilePath = step.getSqlFilePath();
        return r;
    }
}
```

- [ ] **Step 2: 创建 `JobAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.application.dto.JobResponse;
import com.ddh.agent.application.dto.StepResponse;
import com.ddh.agent.domain.model.etl.EtlJob;
import com.ddh.agent.domain.model.etl.EtlRepository;
import com.ddh.agent.domain.model.etl.EtlStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

@Service
public class JobAppService {

    @Autowired private EtlRepository etlRepository;

    public List<JobResponse> listJobs(Long projectId) {
        return etlRepository.findJobsByProjectId(projectId).stream()
            .map(job -> {
                List<StepResponse> steps = stepsOf(job.getId());
                return JobResponse.from(job, steps);
            })
            .collect(Collectors.toList());
    }

    public JobResponse getJob(Long jobId) {
        EtlJob job = etlRepository.findJobById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        return JobResponse.from(job, stepsOf(jobId));
    }

    /** 返回某 step 的 SQL 文件内容（纯文本） */
    public String getSql(Long jobId, Long stepId) {
        EtlStep step = etlRepository.findStepById(stepId)
            .orElseThrow(() -> new RuntimeException("Step not found: " + stepId));
        if (!step.getJobId().equals(jobId)) {
            throw new RuntimeException("Step does not belong to job " + jobId);
        }
        try {
            return new String(Files.readAllBytes(Paths.get(step.getSqlFilePath())),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read SQL file: " + step.getSqlFilePath(), e);
        }
    }

    /** 将 job 的 plan.md + 全部 .sql 打包成 zip 返回字节数组 */
    public byte[] downloadZip(Long jobId) throws IOException {
        EtlJob job = etlRepository.findJobById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        List<EtlStep> steps = etlRepository.findStepsByJobId(jobId);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            // plan.md
            addToZip(zos, job.getPlanMdPath(), "plan.md");
            // SQL files
            for (EtlStep step : steps) {
                String entryName = "step" + step.getStepOrder() + "_"
                    + step.getStepName().replaceAll("[^a-zA-Z0-9_]", "_") + ".sql";
                addToZip(zos, step.getSqlFilePath(), entryName);
            }
        }
        return bos.toByteArray();
    }

    private void addToZip(ZipOutputStream zos, String filePath, String entryName)
        throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return;
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(path, zos);
        zos.closeEntry();
    }

    private List<StepResponse> stepsOf(Long jobId) {
        return etlRepository.findStepsByJobId(jobId).stream()
            .sorted(Comparator.comparing(EtlStep::getStepOrder))
            .map(StepResponse::from)
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 3: 创建 `JobController.java`**

```java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.dto.JobResponse;
import com.ddh.agent.application.service.JobAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class JobController {

    @Autowired private JobAppService jobAppService;

    @GetMapping("/projects/{projectId}/jobs")
    public List<JobResponse> listJobs(@PathVariable Long projectId, Authentication auth) {
        return jobAppService.listJobs(projectId);
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse getJob(@PathVariable Long jobId, Authentication auth) {
        return jobAppService.getJob(jobId);
    }

    @GetMapping(value = "/jobs/{jobId}/steps/{stepId}/sql",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String getSql(@PathVariable Long jobId, @PathVariable Long stepId,
                         Authentication auth) {
        return jobAppService.getSql(jobId, stepId);
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long jobId,
                                           Authentication auth) throws IOException {
        byte[] zip = jobAppService.downloadZip(jobId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"job_" + jobId + ".zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip);
    }
}
```

- [ ] **Step 4: 在 `EtlRepository.java` 补充缺失方法（若 Plan A 未包含）**

```java
// 追加到 domain/model/etl/EtlRepository.java 接口：
Optional<EtlJob> findJobById(Long id);
Optional<EtlStep> findStepById(Long id);
List<EtlStep> findStepsByJobId(Long jobId);
```

在 `EtlRepositoryImpl.java` 追加实现：

```java
@Override
public Optional<EtlJob> findJobById(Long id) {
    return Optional.ofNullable(etlJobMapper.selectById(id));
}

@Override
public Optional<EtlStep> findStepById(Long id) {
    return Optional.ofNullable(etlStepMapper.selectById(id));
}

@Override
public List<EtlStep> findStepsByJobId(Long jobId) {
    return etlStepMapper.selectList(
        new LambdaQueryWrapper<EtlStep>().eq(EtlStep::getJobId, jobId)
            .orderByAsc(EtlStep::getStepOrder));
}
```

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add JobAppService and JobController (list/get/sql/download-zip)"
```

---

## Task 19：AdminAppService + AdminController

**Files:**
- Create: `application/dto/AdminConfigRequest.java`
- Create: `application/dto/AdminConfigResponse.java`
- Create: `application/service/AdminAppService.java`
- Create: `interfaces/rest/AdminController.java`

对应 Python `backend/app/routers/admin.py`：`GET /admin/config` 返回当前 LLM 配置，`PUT /admin/config` 运行时热更新。

运行时配置需要一个可变的 holder bean，不能直接用 `@Value`（Spring 启动后不可变）。

- [ ] **Step 1: 创建配置 holder bean `RuntimeLlmConfig.java`**

```java
package com.ddh.agent.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runtime-mutable LLM configuration. Thread-safe via volatile fields. */
@Component
public class RuntimeLlmConfig {

    private volatile String provider;
    private volatile String claudeApiKey;
    private volatile String claudeModel;
    private volatile String deepseekApiKey;
    private volatile String deepseekModel;
    private volatile String qwenApiKey;
    private volatile String qwenModel;

    @Value("${llm.provider}") public void setProvider(String v)           { this.provider = v; }
    @Value("${llm.claude.api-key:}") public void setClaudeApiKey(String v) { this.claudeApiKey = v; }
    @Value("${llm.claude.model:claude-sonnet-4-6}") public void setClaudeModel(String v) { this.claudeModel = v; }
    @Value("${llm.deepseek.api-key:}") public void setDeepseekApiKey(String v) { this.deepseekApiKey = v; }
    @Value("${llm.deepseek.model:deepseek-chat}") public void setDeepseekModel(String v) { this.deepseekModel = v; }
    @Value("${llm.qwen.api-key:}") public void setQwenApiKey(String v)    { this.qwenApiKey = v; }
    @Value("${llm.qwen.model:qwen-max}") public void setQwenModel(String v) { this.qwenModel = v; }

    public String getProvider()        { return provider; }
    public String getClaudeApiKey()    { return claudeApiKey; }
    public String getClaudeModel()     { return claudeModel; }
    public String getDeepseekApiKey()  { return deepseekApiKey; }
    public String getDeepseekModel()   { return deepseekModel; }
    public String getQwenApiKey()      { return qwenApiKey; }
    public String getQwenModel()       { return qwenModel; }
}
```

- [ ] **Step 2: 创建 DTO**

```java
// application/dto/AdminConfigRequest.java
package com.ddh.agent.application.dto;

public class AdminConfigRequest {
    public String provider;
    public String claudeApiKey;
    public String claudeModel;
    public String deepseekApiKey;
    public String deepseekModel;
    public String qwenApiKey;
    public String qwenModel;
}
```

```java
// application/dto/AdminConfigResponse.java
package com.ddh.agent.application.dto;

public class AdminConfigResponse {
    public String provider;
    public String claudeModel;
    public String deepseekModel;
    public String qwenModel;
    // API keys are masked for security
    public String claudeApiKeyMasked;
    public String deepseekApiKeyMasked;
    public String qwenApiKeyMasked;

    private static String mask(String key) {
        if (key == null || key.length() < 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public static AdminConfigResponse from(com.ddh.agent.infrastructure.config.RuntimeLlmConfig cfg) {
        AdminConfigResponse r = new AdminConfigResponse();
        r.provider = cfg.getProvider();
        r.claudeModel = cfg.getClaudeModel();
        r.deepseekModel = cfg.getDeepseekModel();
        r.qwenModel = cfg.getQwenModel();
        r.claudeApiKeyMasked = mask(cfg.getClaudeApiKey());
        r.deepseekApiKeyMasked = mask(cfg.getDeepseekApiKey());
        r.qwenApiKeyMasked = mask(cfg.getQwenApiKey());
        return r;
    }
}
```

- [ ] **Step 3: 创建 `AdminAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.application.dto.AdminConfigRequest;
import com.ddh.agent.application.dto.AdminConfigResponse;
import com.ddh.agent.domain.service.LlmPort;
import com.ddh.agent.infrastructure.config.RuntimeLlmConfig;
import com.ddh.agent.infrastructure.llm.*;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminAppService {

    @Autowired private RuntimeLlmConfig runtimeLlmConfig;
    @Autowired private OkHttpClient okHttpClient;
    @Autowired private LlmPort llmPortRef;

    public AdminConfigResponse getConfig() {
        return AdminConfigResponse.from(runtimeLlmConfig);
    }

    /**
     * 更新运行时 LLM 配置。
     * 由于 LlmPort @Bean 是单例，热切换 provider 需要更新 holder 中的字段并重新路由。
     * 简单策略：重建适配器并替换 Spring 容器中的 llmPort bean（通过 DynamicLlmPort wrapper）。
     * 当前实现：仅更新 RuntimeLlmConfig 字段，LlmPort 热切换见 Step 4 补充。
     */
    public AdminConfigResponse updateConfig(AdminConfigRequest req) {
        if (req.provider != null) runtimeLlmConfig.setProvider(req.provider);
        if (req.claudeApiKey != null) runtimeLlmConfig.setClaudeApiKey(req.claudeApiKey);
        if (req.claudeModel != null) runtimeLlmConfig.setClaudeModel(req.claudeModel);
        if (req.deepseekApiKey != null) runtimeLlmConfig.setDeepseekApiKey(req.deepseekApiKey);
        if (req.deepseekModel != null) runtimeLlmConfig.setDeepseekModel(req.deepseekModel);
        if (req.qwenApiKey != null) runtimeLlmConfig.setQwenApiKey(req.qwenApiKey);
        if (req.qwenModel != null) runtimeLlmConfig.setQwenModel(req.qwenModel);
        return AdminConfigResponse.from(runtimeLlmConfig);
    }
}
```

- [ ] **Step 4: 热切换 LLM Provider — `DynamicLlmPort` 包装器**

为支持运行时更换 provider，引入一个代理类持有可替换的委托：

```java
// infrastructure/llm/DynamicLlmPort.java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DynamicLlmPort implements LlmPort {

    private final AtomicReference<LlmPort> delegate;

    public DynamicLlmPort(LlmPort initial) {
        this.delegate = new AtomicReference<>(initial);
    }

    public void setDelegate(LlmPort newDelegate) {
        this.delegate.set(newDelegate);
    }

    @Override
    public LlmResponse chatWithTools(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     String systemPrompt) {
        return delegate.get().chatWithTools(messages, tools, systemPrompt);
    }
}
```

修改 `LlmConfig.java`，将 `@Bean` 返回 `DynamicLlmPort`（包裹初始适配器）：

```java
@Bean
public LlmPort llmPort() {
    LlmPort initial = buildAdapter(provider);
    return new DynamicLlmPort(initial);
}

private LlmPort buildAdapter(String p) {
    switch (p) {
        case "deepseek": return new DeepSeekAdapter(okHttpClient, deepseekApiKey, deepseekModel);
        case "qwen":     return new QwenAdapter(okHttpClient, qwenApiKey, qwenModel);
        default:         return new ClaudeAdapter(okHttpClient, claudeApiKey, claudeModel);
    }
}
```

在 `AdminAppService.updateConfig()` 末尾追加热切换逻辑：

```java
@Autowired private LlmPort llmPort;

// 在 updateConfig 末尾：
if (llmPort instanceof DynamicLlmPort) {
    LlmPort newAdapter = buildAdapter(runtimeLlmConfig);
    ((DynamicLlmPort) llmPort).setDelegate(newAdapter);
}
```

在 `AdminAppService` 中新增 `buildAdapter`（引用 `RuntimeLlmConfig` 和 `OkHttpClient`）：

```java
private LlmPort buildAdapter(RuntimeLlmConfig cfg) {
    switch (cfg.getProvider()) {
        case "deepseek":
            return new DeepSeekAdapter(okHttpClient, cfg.getDeepseekApiKey(), cfg.getDeepseekModel());
        case "qwen":
            return new QwenAdapter(okHttpClient, cfg.getQwenApiKey(), cfg.getQwenModel());
        default:
            return new ClaudeAdapter(okHttpClient, cfg.getClaudeApiKey(), cfg.getClaudeModel());
    }
}
```

- [ ] **Step 5: 创建 `AdminController.java`**

```java
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.dto.AdminConfigRequest;
import com.ddh.agent.application.dto.AdminConfigResponse;
import com.ddh.agent.application.service.AdminAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private AdminAppService adminAppService;

    @GetMapping("/config")
    public AdminConfigResponse getConfig(Authentication auth) {
        requireAdmin(auth);
        return adminAppService.getConfig();
    }

    @PutMapping("/config")
    public AdminConfigResponse updateConfig(@RequestBody AdminConfigRequest req,
                                            Authentication auth) {
        requireAdmin(auth);
        return adminAppService.updateConfig(req);
    }

    private void requireAdmin(Authentication auth) {
        if (auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException("Admin only");
        }
    }
}
```

- [ ] **Step 6: Compile check**

```powershell
mvn compile -pl backend-java -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add AdminController with runtime LLM config hot-swap via DynamicLlmPort"
```

---

## Task 20：集成测试

**Files:**
- Create: `src/test/java/com/ddh/agent/AuthControllerTest.java`
- Create: `src/test/java/com/ddh/agent/TableControllerTest.java`
- Create: `src/test/java/com/ddh/agent/ConversationControllerTest.java`
- Create: `src/test/java/com/ddh/agent/JobControllerTest.java`

使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`（更接近真实 HTTP），或 `MockMvc`。以下统一用 MockMvc + `@AutoConfigureMockMvc`。

`application-test.yml` 在 Plan A Task 1 已写好（H2 + 禁用 LLM 调用）。

- [ ] **Step 1: 创建测试基类 `BaseIntegrationTest.java`**

```java
package com.ddh.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    /** Register and login, return Bearer token string */
    protected String obtainToken(String username, String password) throws Exception {
        // Register
        String regBody = mapper.writeValueAsString(
            new java.util.HashMap<String, String>() {{
                put("username", username);
                put("password", password);
                put("email", username + "@test.com");
            }});
        mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content(regBody))
            .andExpect(status().isCreated());

        // Login
        String loginBody = mapper.writeValueAsString(
            new java.util.HashMap<String, String>() {{
                put("username", username);
                put("password", password);
            }});
        MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andReturn();

        String json = result.getResponse().getContentAsString();
        return mapper.readTree(json).path("access_token").asText();
    }
}
```

- [ ] **Step 2: 创建 `AuthControllerTest.java`**

```java
package com.ddh.agent;

import org.junit.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuthControllerTest extends BaseIntegrationTest {

    @Test
    public void registerAndLogin() throws Exception {
        String token = obtainToken("alice_" + System.nanoTime(), "secret123");
        // token must be non-empty
        assert !token.isEmpty();
    }

    @Test
    public void loginWithWrongPassword_returns401() throws Exception {
        long ts = System.nanoTime();
        obtainToken("bob_" + ts, "correct");

        String loginBody = mapper.writeValueAsString(
            new java.util.HashMap<String, String>() {{
                put("username", "bob_" + ts);
                put("password", "wrong");
            }});
        mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void getMe_withValidToken_returns200() throws Exception {
        String token = obtainToken("carol_" + System.nanoTime(), "pass1234");
        mvc.perform(get("/api/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").exists());
    }

    @Test
    public void protectedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: 创建 `TableControllerTest.java`**

```java
package com.ddh.agent;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TableControllerTest extends BaseIntegrationTest {

    private static final String CSV_CONTENT =
        "column_name,data_type,comment\n" +
        "id,bigint,主键\n" +
        "name,varchar,姓名\n";

    @Test
    public void importCsvAndList() throws Exception {
        String token = obtainToken("table_user_" + System.nanoTime(), "pass");

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", CSV_CONTENT.getBytes());

        mvc.perform(multipart("/api/tables/import")
            .file(file)
            .param("name", "test_table")
            .param("description", "test desc")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("test_table"))
            .andExpect(jsonPath("$.columns.length()").value(2));

        mvc.perform(get("/api/tables")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    public void importCsvWithMissingColumn_returns400() throws Exception {
        String token = obtainToken("table_user2_" + System.nanoTime(), "pass");

        String badCsv = "col_name,type\nid,bigint\n";  // missing required headers
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad.csv", "text/csv", badCsv.getBytes());

        mvc.perform(multipart("/api/tables/import")
            .file(file).param("name", "bad")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 4: 创建 `ConversationControllerTest.java`**

```java
package com.ddh.agent;

import org.junit.Test;
import org.springframework.http.MediaType;
import java.util.HashMap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ConversationControllerTest extends BaseIntegrationTest {

    @Test
    public void createConversationAndListMessages() throws Exception {
        String token = obtainToken("conv_user_" + System.nanoTime(), "pass");

        // Create project first
        String projBody = mapper.writeValueAsString(
            new HashMap<String, String>() {{ put("name", "test proj"); put("description", "d"); }});
        String projJson = mvc.perform(post("/api/projects")
            .contentType(MediaType.APPLICATION_JSON).content(projBody)
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long projectId = mapper.readTree(projJson).path("id").asLong();

        // Create conversation
        String convBody = mapper.writeValueAsString(
            new HashMap<String, Object>() {{ put("table_ids", new long[]{}); }});
        String convJson = mvc.perform(post("/api/projects/" + projectId + "/conversations")
            .contentType(MediaType.APPLICATION_JSON).content(convBody)
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long convId = mapper.readTree(convJson).path("id").asLong();

        // POST /chat
        String chatBody = mapper.writeValueAsString(
            new HashMap<String, String>() {{ put("message", "hello agent"); }});
        mvc.perform(post("/api/conversations/" + convId + "/chat")
            .contentType(MediaType.APPLICATION_JSON).content(chatBody)
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // GET /messages
        mvc.perform(get("/api/conversations/" + convId + "/messages")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].role").value("user"))
            .andExpect(jsonPath("$[0].content").value("hello agent"));
    }

    @Test
    public void confirmSchema_changesStateTo3() throws Exception {
        String token = obtainToken("schema_user_" + System.nanoTime(), "pass");

        // Create project + conversation
        String projBody = mapper.writeValueAsString(
            new HashMap<String, String>() {{ put("name", "p"); put("description", "d"); }});
        Long pid = mapper.readTree(mvc.perform(post("/api/projects")
            .contentType(MediaType.APPLICATION_JSON).content(projBody)
            .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString()).path("id").asLong();

        String convBody = mapper.writeValueAsString(
            new HashMap<String, Object>() {{ put("table_ids", new long[]{}); }});
        Long cid = mapper.readTree(mvc.perform(post("/api/projects/" + pid + "/conversations")
            .contentType(MediaType.APPLICATION_JSON).content(convBody)
            .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString()).path("id").asLong();

        // Add user message so state transition is valid
        mvc.perform(post("/api/conversations/" + cid + "/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"test\"}")
            .header("Authorization", "Bearer " + token));

        // confirm-schema
        String schemaBody = mapper.writeValueAsString(
            new HashMap<String, Object>() {{
                put("target_table", "dim_user");
                put("columns", new Object[]{
                    new HashMap<String, String>() {{
                        put("name", "user_id"); put("type", "bigint"); put("comment", "");
                    }}
                });
            }});
        mvc.perform(post("/api/conversations/" + cid + "/confirm-schema")
            .contentType(MediaType.APPLICATION_JSON).content(schemaBody)
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value(3));
    }
}
```

- [ ] **Step 5: 创建 `JobControllerTest.java`**

```java
package com.ddh.agent;

import com.ddh.agent.domain.model.etl.EtlJob;
import com.ddh.agent.domain.model.etl.EtlRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class JobControllerTest extends BaseIntegrationTest {

    @Autowired private EtlRepository etlRepository;

    @Test
    public void listAndGetJob() throws Exception {
        String token = obtainToken("job_user_" + System.nanoTime(), "pass");

        // seed a job directly (EtlDomainService requires real FS; here we bypass)
        EtlJob job = new EtlJob();
        job.setProjectId(1L);
        job.setTargetTable("dim_test");
        job.setTargetSchema("[]");
        job.setPlanMdPath("/tmp/plan.md");
        job.setCreatedAt(LocalDateTime.now());
        EtlJob saved = etlRepository.saveJob(job);

        mvc.perform(get("/api/projects/1/jobs")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mvc.perform(get("/api/jobs/" + saved.getId())
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetTable").value("dim_test"));
    }
}
```

- [ ] **Step 6: 运行所有集成测试**

```powershell
mvn test -pl backend-java
```
Expected: 所有测试通过，`BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/test/
git commit -m "test(java): add integration tests for auth/table/conversation/job controllers"
```

---

## Task 21：最终验收清单

- [ ] **Step 1: 启动完整服务（本地）**

```powershell
# 设置环境变量（示例）
$env:LLM_PROVIDER = "deepseek"
$env:DEEPSEEK_API_KEY = "your-key"

mvn spring-boot:run -pl backend-java `
    -Dspring-boot.run.profiles=default `
    -Dspring.datasource.url="jdbc:mysql://localhost:3306/ddh_agent?serverTimezone=UTC" `
    -Dspring.datasource.username=root `
    -Dspring.datasource.password=root
```

- [ ] **Step 2: Smoke test — 端点兼容性验证清单**

逐一执行以下 curl 命令，确认返回状态码：

```bash
# 1. 注册
curl -s -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","email":"admin@test.com"}' | jq .

# 2. 登录
TOKEN=$(curl -s -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.access_token')
echo "Token: $TOKEN"

# 3. 获取当前用户
curl -s http://localhost:8000/api/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .

# 4. 导入 CSV 表
curl -s -X POST http://localhost:8000/api/tables/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.csv" -F "name=test_tbl" | jq .

# 5. 创建项目
curl -s -X POST http://localhost:8000/api/projects \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"proj1","description":"test"}' | jq .

# 6. 创建对话（替换 PROJECT_ID）
curl -s -X POST http://localhost:8000/api/projects/1/conversations \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"table_ids":[]}' | jq .

# 7. 发送用户消息
curl -s -X POST http://localhost:8000/api/conversations/1/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"请帮我生成 ETL 脚本"}' | jq .

# 8. SSE 流（Ctrl+C 终止）
curl -N http://localhost:8000/api/conversations/1/stream \
  -H "Authorization: Bearer $TOKEN"

# 9. Admin config 查询
curl -s http://localhost:8000/api/admin/config \
  -H "Authorization: Bearer $TOKEN" | jq .
```

- [ ] **Step 3: 前端联调验证**

```powershell
# 启动前端（另开终端）
cd frontend && npm start
# 访问 http://localhost:3000，完整走一遍注册→建项目→建对话→对话→确认 schema→确认步骤→生成 SQL 流程
```

预期结果：
- 前端代码零改动
- SSE 流式输出在 ChatPage 正常渲染
- Job 详情页可下载 zip 包

- [ ] **Step 4: 最终 commit + tag**

```bash
git add .
git commit -m "feat(java): complete Java backend migration — all 21 tasks done"
git tag v1.0.0-java
```

---

**全部 4 个 Plan 文档完成。** 实现顺序：Plan A（基础层）→ Plan B（CRUD）→ Plan C（Agent / SSE）→ Plan D（Job / Admin / 验收）。
