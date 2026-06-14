# DDH Agent 后端 Python → Java 迁移设计文档

**日期：** 2026-06-13  
**作者：** 与 Claude Code 协作完成  
**状态：** 已批准，待实现

---

## 1. 背景与目标

### 1.1 背景

`ddh-agent` 当前后端为 Python（FastAPI + SQLAlchemy），运行良好但团队希望切换至 Java 技术栈以便与企业内部 Java 生态对齐。

### 1.2 目标

- 将现有 Python 后端完整迁移至 Java Spring Boot，功能对等
- REST API 接口路径、请求/响应 JSON 结构完全兼容，前端零改动
- 流式 SSE 响应格式不变，前端 `EventSource` 代码无需修改
- BCrypt 密码 hash 与 Python `passlib[bcrypt]` 兼容，存量用户无需重置密码
- 采用 DDD 四层架构，代码结构清晰，领域层不依赖任何框架

### 1.3 不在范围内

- 前端代码修改
- 数据库 schema 变更
- 新功能开发

---

## 2. 技术选型

| 职责 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 8 | 企业标准，LTS |
| Web 框架 | Spring Boot | 2.7.x | Java 8 兼容的最新稳定版（3.x 要求 Java 17） |
| ORM | MyBatis Plus | 3.5.x | 替代 SQLAlchemy，Mapper 接口 + LambdaQueryWrapper |
| 数据库 | MySQL | 8.x | 沿用现有 schema，驱动 `mysql-connector-java` |
| JWT | jjwt | 0.11.x | 替代 `python-jose`，token 格式兼容 |
| 密码加密 | Spring Security BCrypt | — | 与 Python `passlib[bcrypt]` 输出完全兼容 |
| 安全框架 | Spring Security | 5.7.x | JWT 无状态认证，无 Session |
| SSE 流式 | Spring MVC SseEmitter | — | 替代 FastAPI `StreamingResponse` |
| LLM HTTP | OkHttp3 | 4.12.x | 直调 Claude / DeepSeek / Qwen API，Java 8 友好 |
| JSON | Jackson | 2.x | Spring Boot 默认，替代 Pydantic 序列化 |
| 代码简化 | Lombok | 1.18.x | 消除 getter/setter/builder 样板 |
| 构建工具 | Maven | 3.x | 企业标准，依赖管理成熟 |

---

## 3. 项目结构

### 3.1 目录位置

```
ddh-agent/
├── backend/              # 现有 Python 后端（保留，切换完成后再删除）
├── backend-java/         # 新 Java 后端（本次实现目标）
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/ddh/agent/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── mapper/           # MyBatis XML 文件
│       └── test/
└── frontend/             # 不变
```

### 3.2 DDD 四层包结构

```
com.ddh.agent/
│
├── interfaces/                            # 接口层：HTTP 入口，不含业务逻辑
│   ├── rest/
│   │   ├── AuthController.java            # POST /api/auth/register, /api/auth/token
│   │   ├── ProjectController.java         # CRUD /api/projects, 表关联
│   │   ├── ConversationController.java    # 对话 CRUD + SSE /chat
│   │   ├── TableController.java           # 数据源表管理
│   │   ├── JobController.java             # ETL Job 查询与执行
│   │   └── AdminController.java           # 管理员接口
│   └── dto/
│       ├── request/                       # 入参 DTO（对应 Python schemas/ *In 类）
│       │   ├── RegisterRequest.java
│       │   ├── LoginRequest.java
│       │   ├── ProjectCreateRequest.java
│       │   ├── ProjectUpdateRequest.java
│       │   ├── TableAssociateRequest.java
│       │   ├── CreateConversationRequest.java
│       │   ├── SetConversationTablesRequest.java
│       │   ├── ChatRequest.java
│       │   ├── ConfirmSchemaRequest.java
│       │   └── ConfirmStepsRequest.java
│       └── response/                      # 出参 DTO（对应 Python schemas/ *Out 类）
│           ├── TokenResponse.java
│           ├── UserResponse.java
│           ├── ProjectResponse.java
│           ├── ConversationResponse.java  # 含 tableIds: List<Long>
│           ├── MessageResponse.java
│           ├── TableDetailResponse.java   # 含 columns 列表
│           ├── EtlJobResponse.java
│           └── EtlStepResponse.java
│
├── application/                           # 应用层：用例编排，调用 domain + infrastructure
│   ├── service/
│   │   ├── AuthAppService.java            # 注册、登录、token 签发
│   │   ├── ProjectAppService.java         # 项目 CRUD、表关联管理
│   │   ├── ConversationAppService.java    # 对话创建、消息读取、表关联
│   │   ├── TableAppService.java           # 数据源表 CRUD、列管理
│   │   ├── AgentAppService.java           # SSE 流式 Agent 调用入口
│   │   └── JobAppService.java             # ETL Job 查询、步骤执行
│   └── assembler/                         # 领域对象 → 响应 DTO 转换
│       ├── ProjectAssembler.java
│       ├── ConversationAssembler.java
│       └── TableAssembler.java
│
├── domain/                                # 领域层：核心业务规则，不依赖任何框架
│   ├── model/
│   │   ├── user/
│   │   │   ├── User.java                  # 实体：id, username, email, hashedPassword, isAdmin
│   │   │   └── UserRepository.java        # 仓储接口（Port）
│   │   ├── project/
│   │   │   ├── Project.java               # 实体：id, name, description, ownerId
│   │   │   ├── ProjectTable.java          # 关联：projectId, tableId
│   │   │   └── ProjectRepository.java
│   │   ├── conversation/
│   │   │   ├── Conversation.java          # 实体：id, projectId, state(1-5)
│   │   │   ├── Message.java               # 实体：id, conversationId, role, content
│   │   │   ├── ConversationTable.java     # 关联：conversationId, tableId
│   │   │   └── ConversationRepository.java
│   │   ├── table/
│   │   │   ├── SourceTable.java           # 实体：id, name, description
│   │   │   ├── TableColumn.java           # 实体：id, tableId, columnName, dataType, comment, sortOrder
│   │   │   └── SourceTableRepository.java
│   │   └── etl/
│   │       ├── EtlJob.java                # 实体：id, projectId, targetTable, planMdPath
│   │       ├── EtlStep.java               # 实体：id, jobId, stepOrder, stepName, sqlFilePath
│   │       └── EtlRepository.java
│   └── service/
│       ├── AgentDomainService.java        # Agent 状态机逻辑、工具调用、消息历史构建
│       └── EtlDomainService.java          # ETL plan 生成、Job/Step 持久化
│
└── infrastructure/                        # 基础设施层：技术实现细节
    ├── persistence/
    │   ├── mapper/                        # MyBatis Plus Mapper 接口
    │   │   ├── UserMapper.java
    │   │   ├── ProjectMapper.java
    │   │   ├── ProjectTableMapper.java
    │   │   ├── SourceTableMapper.java
    │   │   ├── TableColumnMapper.java
    │   │   ├── ConversationMapper.java
    │   │   ├── ConversationTableMapper.java
    │   │   ├── MessageMapper.java
    │   │   ├── EtlJobMapper.java
    │   │   └── EtlStepMapper.java
    │   └── repository/                    # 仓储接口实现（Adapter）
    │       ├── UserRepositoryImpl.java
    │       ├── ProjectRepositoryImpl.java
    │       ├── ConversationRepositoryImpl.java
    │       ├── SourceTableRepositoryImpl.java
    │       └── EtlRepositoryImpl.java
    ├── llm/                               # LLM Provider 适配器
    │   ├── LlmPort.java                   # 接口：ChatResponse chatWithTools(...)
    │   ├── dto/
    │   │   ├── LlmMessage.java            # role + content
    │   │   ├── LlmToolCall.java           # id + name + input(Map)
    │   │   └── LlmChatResponse.java       # text + stopReason + toolCalls
    │   ├── ClaudeAdapter.java             # 调用 Anthropic Messages API
    │   ├── DeepSeekAdapter.java           # OpenAI 兼容接口
    │   ├── QwenAdapter.java               # 阿里云 DashScope API
    │   └── LlmProviderFactory.java        # 根据配置返回对应 Provider Bean
    ├── security/
    │   ├── JwtUtil.java                   # token 生成 / 解析 / 校验
    │   ├── JwtAuthFilter.java             # OncePerRequestFilter，从 Bearer 头提取用户
    │   └── UserDetailsServiceImpl.java    # 加载 UserDetails，供 Spring Security 使用
    └── config/
        ├── SecurityConfig.java            # 白名单、STATELESS session、BCrypt Bean
        ├── CorsConfig.java                # 允许 http://localhost:3000
        ├── MybatisPlusConfig.java         # 分页插件等
        └── AppConfig.java                 # OkHttpClient Bean、线程池 Bean
```

---

## 4. 层间依赖规则

```
interfaces → application → domain ← infrastructure
```

- `interfaces` 只依赖 `application`（调用 AppService）和 `dto`
- `application` 依赖 `domain`（Repository 接口、领域服务）
- `domain` **不依赖** Spring、MyBatis 等任何框架；仅含纯 Java 接口和 POJO
- `infrastructure` 实现 `domain` 中定义的 Repository 接口和 LlmPort 接口

---

## 5. 关键模块详细设计

### 5.1 JWT 认证

**流程：**
1. `POST /api/auth/token`（login）→ `AuthAppService` 校验用户名/密码 → `JwtUtil.generateToken(username)` → 返回 `access_token`
2. 后续请求带 `Authorization: Bearer <token>` → `JwtAuthFilter` 解析 token → 写入 `SecurityContextHolder`
3. Controller 通过 `@AuthenticationPrincipal` 或 `SecurityContextHolder` 获取当前用户

**兼容性：**
- `BCryptPasswordEncoder.matches(rawPassword, storedHash)` 直接兼容 Python `passlib` 生成的 `$2b$` 格式 hash
- 存量 MySQL 用户数据无需任何迁移

**Token 配置（`application.yml`）：**
```yaml
app:
  secret-key: "your-secret-key"
  token-expire-minutes: 1440
```

### 5.2 SSE 流式响应

**接口：** `GET /api/conversations/{id}/chat`（与 Python 一致）

**实现：**
```java
// ConversationController
@GetMapping(value = "/conversations/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@PathVariable Long id, @RequestParam String message, ...) {
    SseEmitter emitter = new SseEmitter(120_000L);
    agentAppService.runAndStream(id, message, emitter);
    return emitter;
}

// AgentAppService
public void runAndStream(Long convId, String message, SseEmitter emitter) {
    executorService.submit(() -> {
        try {
            agentDomainService.run(convId, message, event -> {
                emitter.send(SseEmitter.event().data(Jackson.toJson(event)));
            });
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
}
```

**事件格式（与 Python 完全一致）：**
```json
{"type": "token", "text": "..."}
{"type": "waiting", "state": 2, "message": "..."}
{"type": "done", "job_id": 1}
{"type": "stream_end"}
{"type": "error", "message": "..."}
```

### 5.3 LLM Provider 适配器

**接口定义（`domain` 层）：**
```java
public interface LlmPort {
    LlmChatResponse chatWithTools(List<LlmMessage> messages, List<Map<String, Object>> tools, String system);
}
```

**工厂注入（`infrastructure/llm/LlmProviderFactory.java`）：**
```java
@Bean
public LlmPort llmPort(@Value("${llm.provider}") String provider, ...) {
    switch (provider) {
        case "claude":    return new ClaudeAdapter(...);
        case "deepseek":  return new DeepSeekAdapter(...);
        case "qwen":      return new QwenAdapter(...);
        default: throw new IllegalArgumentException("Unknown LLM provider: " + provider);
    }
}
```

**HTTP 调用（OkHttp3）：**
- `ClaudeAdapter`：调用 `https://api.anthropic.com/v1/messages`，Header 带 `x-api-key` 和 `anthropic-version: 2023-06-01`
- `DeepSeekAdapter` / `QwenAdapter`：OpenAI 兼容接口，`POST /v1/chat/completions`，Header 带 `Authorization: Bearer <key>`

### 5.4 Agent 状态机（AgentDomainService）

对应 Python `agent_service.py` 的核心循环，逻辑不变：

```
state=1 → list_project_tables / get_table_schema → propose_schema → state=2（等待确认）
state=2 → 前端确认后 → state=3
state=3 → propose_etl_steps → state=4（等待确认）
state=4 → generate_sql × N → 写 EtlJob/EtlStep → state=5（完成）
```

**工具执行映射（对应 Python `agent_tools.py`）：**

| 工具名 | Java 实现位置 | 说明 |
|--------|--------------|------|
| `list_project_tables` | `AgentDomainService` | 查 ProjectTable + SourceTable |
| `get_table_schema` | `AgentDomainService` | 查 TableColumn |
| `propose_schema` | `AgentDomainService` | 发 schema_proposal 事件，conversation.state → 2 |
| `propose_etl_steps` | `AgentDomainService` | 发 steps_proposal 事件，state → 4 |
| `generate_sql` | `AgentDomainService` | 写 SQL 文件，收集生成步骤 |

### 5.5 MyBatis Plus 数据访问

**简单查询用 LambdaQueryWrapper：**
```java
// 查项目下的所有对话
conversationMapper.selectList(
    new LambdaQueryWrapper<Conversation>()
        .eq(Conversation::getProjectId, projectId)
        .orderByDesc(Conversation::getCreatedAt)
);
```

**复杂查询（多表 join）用 Mapper XML：**
```xml
<!-- resources/mapper/SourceTableMapper.xml -->
<select id="selectWithColumnsByProjectId" resultMap="TableDetailResultMap">
    SELECT t.id, t.name, t.description,
           c.id as col_id, c.column_name, c.data_type, c.comment, c.sort_order
    FROM source_tables t
    JOIN project_tables pt ON pt.table_id = t.id
    LEFT JOIN table_columns c ON c.table_id = t.id
    WHERE pt.project_id = #{projectId}
    ORDER BY t.id, c.sort_order
</select>
```

---

## 6. 配置文件

### 6.1 `application.yml`

```yaml
server:
  port: 8000

app:
  secret-key: ""
  token-expire-minutes: 1440

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ddh_agent?useSSL=false&characterEncoding=utf8&serverTimezone=UTC
    username: root
    password: ""
    driver-class-name: com.mysql.cj.jdbc.Driver
  jackson:
    naming-strategy: com.fasterxml.jackson.databind.PropertyNamingStrategies$SnakeCaseStrategy

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

llm:
  provider: claude
  claude:
    api-key: ""
    model: claude-sonnet-4-6
  deepseek:
    api-key: ""
    model: deepseek-chat
  qwen:
    api-key: ""
    model: qwen-max

files:
  projects-dir: ./projects

logging:
  level:
    com.ddh.agent: DEBUG
```

### 6.2 Jackson 下划线策略

Python 接口使用 `snake_case`（如 `project_id`、`table_ids`），Java 实体使用 `camelCase`。通过 Jackson 全局 `SnakeCaseStrategy` 自动转换，无需在每个字段上加 `@JsonProperty`。

---

## 7. API 接口对照表

| Method | Path | Controller | 说明 |
|--------|------|-----------|------|
| POST | `/api/auth/register` | AuthController | 注册 |
| POST | `/api/auth/token` | AuthController | 登录，返回 JWT |
| GET | `/api/auth/me` | AuthController | 当前用户信息 |
| GET | `/api/projects` | ProjectController | 项目列表 |
| POST | `/api/projects` | ProjectController | 创建项目 |
| PUT | `/api/projects/{id}` | ProjectController | 更新项目 |
| DELETE | `/api/projects/{id}` | ProjectController | 删除项目 |
| GET | `/api/projects/{id}/tables` | ProjectController | 项目关联的表 |
| POST | `/api/projects/{id}/tables` | ProjectController | 关联表 |
| DELETE | `/api/projects/{id}/tables/{tableId}` | ProjectController | 解除关联 |
| GET | `/api/projects/{id}/tables-with-details` | ProjectController | 含列详情的表列表 |
| POST | `/api/projects/{id}/conversations` | ConversationController | 创建对话（含 table_ids） |
| GET | `/api/projects/{id}/conversations` | ConversationController | 对话列表 |
| GET | `/api/conversations/{id}/chat` | ConversationController | SSE 流式对话 |
| POST | `/api/conversations/{id}/confirm-schema` | ConversationController | 确认目标表结构 |
| POST | `/api/conversations/{id}/confirm-steps` | ConversationController | 确认 ETL 步骤 |
| GET | `/api/conversations/{id}/messages` | ConversationController | 消息历史 |
| GET | `/api/conversations/{id}/tables` | ConversationController | 对话关联的表 |
| PUT | `/api/conversations/{id}/tables` | ConversationController | 更新对话关联表 |
| GET | `/api/tables` | TableController | 数据源表列表 |
| POST | `/api/tables` | TableController | 创建数据源表 |
| PUT | `/api/tables/{id}` | TableController | 更新数据源表 |
| DELETE | `/api/tables/{id}` | TableController | 删除数据源表 |
| GET | `/api/jobs` | JobController | ETL Job 列表 |
| GET | `/api/jobs/{id}` | JobController | Job 详情 |
| POST | `/api/jobs/{id}/run` | JobController | 执行 Job |
| GET | `/api/admin/users` | AdminController | 用户列表（管理员） |

---

## 8. pom.xml 核心依赖

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <!-- MyBatis Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.7</version>
    </dependency>
    <!-- MySQL -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <!-- OkHttp3 for LLM API calls -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 9. 迁移实施顺序

按以下顺序实现，每步可独立测试：

1. **项目脚手架** — `pom.xml`、`application.yml`、`DdhAgentApplication.java`、基础 `config/` 类
2. **基础设施层** — MyBatis Plus 配置、所有 Mapper、Repository 实现、`JwtUtil`、`JwtAuthFilter`、`SecurityConfig`
3. **领域层实体与接口** — 所有 domain model POJO、Repository 接口、`LlmPort` 接口
4. **Auth 模块** — `AuthAppService` + `AuthController`（注册/登录/me）
5. **Table 模块** — `TableAppService` + `TableController`
6. **Project 模块** — `ProjectAppService` + `ProjectController`（含 tables-with-details）
7. **Conversation 基础 CRUD** — 创建/列表/消息/表关联接口
8. **LLM 适配器** — `ClaudeAdapter`、`DeepSeekAdapter`、`QwenAdapter`
9. **Agent 核心** — `AgentDomainService`（状态机 + 工具调用循环）+ `AgentAppService`（SSE 编排）
10. **Job 模块** — `JobAppService` + `JobController`
11. **Admin 模块** — `AdminController`
12. **集成测试** — 使用现有 MySQL 数据库端到端验证全部接口

---

## 10. 风险与注意事项

| 风险 | 应对措施 |
|------|---------|
| BCrypt hash 格式不兼容 | Python `passlib` 生成 `$2b$`，Spring Security BCrypt 同样支持 `$2b$`，直接兼容 |
| SSE 事件格式不一致 | 以 Python 现有事件 JSON 为标准，Java 侧做单元测试验证格式 |
| LLM API 工具调用 JSON 结构差异 | 参考 Python `claude_provider.py` 中的请求体结构，逐字段对齐 |
| Java 8 缺少新 API | 使用 `Optional`、`CompletableFuture`；避免 `var`、`record` 等 Java 11+ 语法 |
| 文件系统（SQL 文件写入） | `EtlDomainService` 中保持相同目录结构 `projects/{project_id}/...`，与 Python 的 `etl_service.py` 一致 |
