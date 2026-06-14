# DDH Agent Java 迁移 — Plan A：脚手架 + 实体 + 安全层

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `backend-java/` 下搭建 Spring Boot 2.7 项目骨架，完成所有领域实体、Repository 接口与实现、MyBatis Plus Mapper、JWT 安全层，让项目能启动并连接 MySQL。

**Architecture:** DDD 四层；本计划只涉及 infrastructure 和 domain 两层，application/interfaces 层留给后续计划。

**Tech Stack:** Java 8, Spring Boot 2.7.18, MyBatis Plus 3.5.7, MySQL 8, jjwt 0.11.5, Lombok, Maven 3, H2（仅测试）

---

## 文件清单

```
backend-java/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/ddh/agent/
│   │   │   ├── DdhAgentApplication.java
│   │   │   ├── domain/model/
│   │   │   │   ├── user/User.java
│   │   │   │   ├── user/UserRepository.java
│   │   │   │   ├── project/Project.java
│   │   │   │   ├── project/ProjectTable.java
│   │   │   │   ├── project/ProjectRepository.java
│   │   │   │   ├── table/SourceTable.java
│   │   │   │   ├── table/TableColumn.java
│   │   │   │   ├── table/SourceTableRepository.java
│   │   │   │   ├── conversation/Conversation.java
│   │   │   │   ├── conversation/Message.java
│   │   │   │   ├── conversation/ConversationTable.java
│   │   │   │   ├── conversation/ConversationRepository.java
│   │   │   │   ├── etl/EtlJob.java
│   │   │   │   ├── etl/EtlStep.java
│   │   │   │   └── etl/EtlRepository.java
│   │   │   ├── domain/service/LlmPort.java
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/mapper/  (10 Mapper 接口)
│   │   │   │   ├── persistence/repository/ (5 Repository 实现)
│   │   │   │   ├── security/JwtUtil.java
│   │   │   │   ├── security/JwtAuthFilter.java
│   │   │   │   ├── security/UserDetailsServiceImpl.java
│   │   │   │   └── config/
│   │   │   │       ├── SecurityConfig.java
│   │   │   │       ├── CorsConfig.java
│   │   │   │       ├── MybatisPlusConfig.java
│   │   │   │       └── AppConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── mapper/SourceTableMapper.xml
│   └── test/
│       ├── java/com/ddh/agent/
│       │   ├── infrastructure/security/JwtUtilTest.java
│       │   └── infrastructure/persistence/UserRepositoryTest.java
│       └── resources/
│           ├── application-test.yml
│           └── schema.sql
```

---

## Task 1：Maven 项目脚手架

**Files:**
- Create: `backend-java/pom.xml`
- Create: `backend-java/src/main/java/com/ddh/agent/DdhAgentApplication.java`
- Create: `backend-java/src/main/resources/application.yml`
- Create: `backend-java/src/test/resources/application-test.yml`
- Create: `backend-java/src/test/resources/schema.sql`

- [ ] **Step 1: 创建 `backend-java/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.ddh</groupId>
    <artifactId>ddh-agent</artifactId>
    <version>0.1.0</version>
    <name>ddh-agent</name>

    <properties>
        <java.version>8</java.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <jjwt.version>0.11.5</jjwt.version>
        <okhttp.version>4.12.0</okhttp.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>${okhttp.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 `DdhAgentApplication.java`**

```java
package com.ddh.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DdhAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DdhAgentApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 `src/main/resources/application.yml`**

```yaml
server:
  port: 8000

app:
  secret-key: "change-me-in-production-use-32plus-chars"
  token-expire-minutes: 1440

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ddh_agent?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: ""
    driver-class-name: com.mysql.cj.jdbc.Driver
  jackson:
    property-naming-strategy: SNAKE_CASE

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    banner: false

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

- [ ] **Step 4: 创建 `src/test/resources/application-test.yml`**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  jackson:
    property-naming-strategy: SNAKE_CASE

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    banner: false

app:
  secret-key: "test-secret-key-at-least-32-characters!!"
  token-expire-minutes: 60

llm:
  provider: claude
  claude:
    api-key: "test-key"
    model: claude-sonnet-4-6

files:
  projects-dir: ./test-projects
```

- [ ] **Step 5: 创建 `src/test/resources/schema.sql`**

```sql
DROP TABLE IF EXISTS etl_steps;
DROP TABLE IF EXISTS etl_jobs;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversation_tables;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS table_columns;
DROP TABLE IF EXISTS source_tables;
DROP TABLE IF EXISTS project_tables;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    email      VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role       SMALLINT DEFAULT 2,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    owner_id    BIGINT NOT NULL,
    status      SMALLINT DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_tables (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    table_id   BIGINT NOT NULL
);

CREATE TABLE source_tables (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    scope       SMALLINT DEFAULT 2,
    owner_id    BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE table_columns (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id    BIGINT NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    data_type   VARCHAR(64)  NOT NULL,
    comment     TEXT,
    sort_order  INT DEFAULT 0
);

CREATE TABLE conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    state      INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversation_tables (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    table_id        BIGINT NOT NULL
);

CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE etl_jobs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT NOT NULL,
    target_table  VARCHAR(128) NOT NULL,
    target_schema TEXT,
    plan_md_path  VARCHAR(512),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE etl_steps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id        BIGINT NOT NULL,
    step_order    INT NOT NULL,
    step_name     VARCHAR(256) NOT NULL,
    is_temp_table SMALLINT DEFAULT 0,
    sql_file_path VARCHAR(512)
);
```

- [ ] **Step 6: 验证编译**

```powershell
cd backend-java
mvn compile -q
```
Expected: `BUILD SUCCESS`（会下载依赖，首次耗时较长）

- [ ] **Step 7: Commit**

```bash
git add backend-java/
git commit -m "feat(java): bootstrap Maven project scaffold with config and test schema"
```

---

## Task 2：领域实体 — User、Project、SourceTable

**Files:**
- Create: `src/main/java/com/ddh/agent/domain/model/user/User.java`
- Create: `src/main/java/com/ddh/agent/domain/model/project/Project.java`
- Create: `src/main/java/com/ddh/agent/domain/model/project/ProjectTable.java`
- Create: `src/main/java/com/ddh/agent/domain/model/table/SourceTable.java`
- Create: `src/main/java/com/ddh/agent/domain/model/table/TableColumn.java`

- [ ] **Step 1: 创建 `User.java`**

```java
package com.ddh.agent.domain.model.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    /** 1=admin 2=member */
    private Integer role;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 `Project.java`**

```java
package com.ddh.agent.domain.model.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    /** 1=draft 2=in_progress 3=done */
    private Integer status;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 `ProjectTable.java`**

```java
package com.ddh.agent.domain.model.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("project_tables")
public class ProjectTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long tableId;
}
```

- [ ] **Step 4: 创建 `SourceTable.java`**

```java
package com.ddh.agent.domain.model.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("source_tables")
public class SourceTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    /** 1=public 2=private */
    private Integer scope;
    private Long ownerId;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 `TableColumn.java`**

```java
package com.ddh.agent.domain.model.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("table_columns")
public class TableColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
}
```

- [ ] **Step 6: 编译确认**

```powershell
mvn compile -q
```
Expected: `BUILD SUCCESS`

---

## Task 3：领域实体 — Conversation、Message、EtlJob、EtlStep

**Files:**
- Create: `domain/model/conversation/Conversation.java`
- Create: `domain/model/conversation/Message.java`
- Create: `domain/model/conversation/ConversationTable.java`
- Create: `domain/model/etl/EtlJob.java`
- Create: `domain/model/etl/EtlStep.java`

- [ ] **Step 1: 创建 `Conversation.java`**

```java
package com.ddh.agent.domain.model.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 1=需求分析 2=等待schema确认 3=规划步骤 4=生成SQL 5=完成 */
    private Integer state;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 `Message.java`**

```java
package com.ddh.agent.domain.model.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    /** user / assistant / tool */
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 `ConversationTable.java`**

```java
package com.ddh.agent.domain.model.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("conversation_tables")
public class ConversationTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long tableId;
}
```

- [ ] **Step 4: 创建 `EtlJob.java`**

```java
package com.ddh.agent.domain.model.etl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("etl_jobs")
public class EtlJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String targetTable;
    /** JSON 序列化存储，格式：[{"name":"col","type":"VARCHAR","comment":"..."}] */
    private String targetSchema;
    private String planMdPath;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 `EtlStep.java`**

```java
package com.ddh.agent.domain.model.etl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("etl_steps")
public class EtlStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private Integer stepOrder;
    private String stepName;
    /** 0=final table 1=temp table */
    private Integer isTempTable;
    private String sqlFilePath;
}
```

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/
git commit -m "feat(java): add all domain entities (User, Project, Conversation, Etl)"
```

---

## Task 4：领域 Repository 接口 + LlmPort

**Files:**
- Create: `domain/model/user/UserRepository.java`
- Create: `domain/model/project/ProjectRepository.java`
- Create: `domain/model/table/SourceTableRepository.java`
- Create: `domain/model/conversation/ConversationRepository.java`
- Create: `domain/model/etl/EtlRepository.java`
- Create: `domain/service/LlmPort.java`

- [ ] **Step 1: 创建 `UserRepository.java`**

```java
package com.ddh.agent.domain.model.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    User save(User user);
    List<User> findAll();
}
```

- [ ] **Step 2: 创建 `ProjectRepository.java`**

```java
package com.ddh.agent.domain.model.project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findById(Long id);
    List<Project> findByOwnerId(Long ownerId);
    Project save(Project project);
    void deleteById(Long id);

    List<ProjectTable> findTablesByProjectId(Long projectId);
    boolean existsProjectTable(Long projectId, Long tableId);
    void addProjectTable(Long projectId, Long tableId);
    void removeProjectTable(Long projectId, Long tableId);
}
```

- [ ] **Step 3: 创建 `SourceTableRepository.java`**

```java
package com.ddh.agent.domain.model.table;

import java.util.List;
import java.util.Optional;

public interface SourceTableRepository {
    Optional<SourceTable> findById(Long id);
    List<SourceTable> findVisible(Long currentUserId);
    SourceTable save(SourceTable table);
    void deleteById(Long id);

    List<TableColumn> findColumnsByTableId(Long tableId);
    void saveColumns(List<TableColumn> columns);
    void deleteColumnsByTableId(Long tableId);

    /** 查询项目关联的表及其列详情，用于 tables-with-details 接口 */
    List<TableWithColumns> findWithColumnsByProjectId(Long projectId);
    /** 查询会话关联的表及其列详情 */
    List<TableWithColumns> findWithColumnsByConversationId(Long conversationId);
}
```

- [ ] **Step 4: 创建 `TableWithColumns.java`（SourceTableRepository 返回值 VO）**

```java
package com.ddh.agent.domain.model.table;

import lombok.Data;
import java.util.List;

@Data
public class TableWithColumns {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private List<TableColumn> columns;
}
```

- [ ] **Step 5: 创建 `ConversationRepository.java`**

```java
package com.ddh.agent.domain.model.conversation;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {
    Optional<Conversation> findById(Long id);
    List<Conversation> findByProjectId(Long projectId);
    Conversation save(Conversation conversation);

    List<ConversationTable> findTablesByConversationId(Long conversationId);
    void replaceConversationTables(Long conversationId, List<Long> tableIds);

    List<Message> findMessagesByConversationId(Long conversationId);
    Message saveMessage(Message message);
}
```

- [ ] **Step 6: 创建 `EtlRepository.java`**

```java
package com.ddh.agent.domain.model.etl;

import java.util.List;
import java.util.Optional;

public interface EtlRepository {
    Optional<EtlJob> findJobById(Long id);
    List<EtlJob> findJobsByProjectId(Long projectId);
    EtlJob saveJob(EtlJob job);

    List<EtlStep> findStepsByJobId(Long jobId);
    Optional<EtlStep> findStepById(Long id);
    EtlStep saveStep(EtlStep step);
}
```

- [ ] **Step 7: 创建 `LlmPort.java`**

```java
package com.ddh.agent.domain.service;

import java.util.List;
import java.util.Map;

public interface LlmPort {

    LlmResponse chatWithTools(List<Map<String, Object>> messages,
                              List<Map<String, Object>> tools,
                              String systemPrompt);

    class LlmResponse {
        public final String text;
        public final String stopReason;
        public final List<ToolCall> toolCalls;

        public LlmResponse(String text, String stopReason, List<ToolCall> toolCalls) {
            this.text = text;
            this.stopReason = stopReason;
            this.toolCalls = toolCalls;
        }

        public Map<String, Object> toAssistantMessage() {
            java.util.Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("role", "assistant");
            msg.put("content", text != null ? text : "");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                msg.put("tool_calls", toolCalls);
            }
            return msg;
        }
    }

    class ToolCall {
        public final String id;
        public final String name;
        public final Map<String, Object> input;

        public ToolCall(String id, String name, Map<String, Object> input) {
            this.id = id;
            this.name = name;
            this.input = input;
        }
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/main/java/
git commit -m "feat(java): add domain repository interfaces and LlmPort"
```

---

## Task 5：MyBatis Plus Mapper 接口 + XML

**Files:**
- Create: `infrastructure/persistence/mapper/UserMapper.java`
- Create: `infrastructure/persistence/mapper/ProjectMapper.java`
- Create: `infrastructure/persistence/mapper/ProjectTableMapper.java`
- Create: `infrastructure/persistence/mapper/SourceTableMapper.java`
- Create: `infrastructure/persistence/mapper/TableColumnMapper.java`
- Create: `infrastructure/persistence/mapper/ConversationMapper.java`
- Create: `infrastructure/persistence/mapper/ConversationTableMapper.java`
- Create: `infrastructure/persistence/mapper/MessageMapper.java`
- Create: `infrastructure/persistence/mapper/EtlJobMapper.java`
- Create: `infrastructure/persistence/mapper/EtlStepMapper.java`
- Create: `src/main/resources/mapper/SourceTableMapper.xml`

- [ ] **Step 1: 创建 `UserMapper.java`（其余 Mapper 同此模式）**

```java
package com.ddh.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.agent.domain.model.user.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

按照完全相同的模式创建以下 9 个 Mapper（只改 package 中的类名和泛型类型）：

| 文件 | 泛型类型 |
|------|---------|
| `ProjectMapper.java` | `Project` |
| `ProjectTableMapper.java` | `ProjectTable` |
| `SourceTableMapper.java` | `SourceTable` |
| `TableColumnMapper.java` | `TableColumn` |
| `ConversationMapper.java` | `Conversation` |
| `ConversationTableMapper.java` | `ConversationTable` |
| `MessageMapper.java` | `Message` |
| `EtlJobMapper.java` | `EtlJob` |
| `EtlStepMapper.java` | `EtlStep` |

- [ ] **Step 2: 创建 `src/main/resources/mapper/SourceTableMapper.xml`**

此 XML 实现 `findWithColumnsByProjectId` 和 `findWithColumnsByConversationId` 两个复杂查询，在 Task 6 的 Repository 实现中调用。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ddh.agent.infrastructure.persistence.mapper.SourceTableMapper">

    <resultMap id="TableWithColumnsMap"
               type="com.ddh.agent.domain.model.table.TableWithColumns">
        <id     property="id"          column="t_id"/>
        <result property="name"        column="t_name"/>
        <result property="description" column="t_description"/>
        <result property="scope"       column="t_scope"/>
        <result property="ownerId"     column="t_owner_id"/>
        <collection property="columns"
                    ofType="com.ddh.agent.domain.model.table.TableColumn">
            <id     property="id"         column="c_id"/>
            <result property="tableId"    column="c_table_id"/>
            <result property="columnName" column="c_column_name"/>
            <result property="dataType"   column="c_data_type"/>
            <result property="comment"    column="c_comment"/>
            <result property="sortOrder"  column="c_sort_order"/>
        </collection>
    </resultMap>

    <select id="selectWithColumnsByProjectId"
            parameterType="long"
            resultMap="TableWithColumnsMap">
        SELECT t.id          AS t_id,
               t.name        AS t_name,
               t.description AS t_description,
               t.scope       AS t_scope,
               t.owner_id    AS t_owner_id,
               c.id          AS c_id,
               c.table_id    AS c_table_id,
               c.column_name AS c_column_name,
               c.data_type   AS c_data_type,
               c.comment     AS c_comment,
               c.sort_order  AS c_sort_order
        FROM   source_tables t
        JOIN   project_tables pt ON pt.table_id = t.id
        LEFT JOIN table_columns c ON c.table_id = t.id
        WHERE  pt.project_id = #{projectId}
        ORDER  BY t.id, c.sort_order
    </select>

    <select id="selectWithColumnsByConversationId"
            parameterType="long"
            resultMap="TableWithColumnsMap">
        SELECT t.id          AS t_id,
               t.name        AS t_name,
               t.description AS t_description,
               t.scope       AS t_scope,
               t.owner_id    AS t_owner_id,
               c.id          AS c_id,
               c.table_id    AS c_table_id,
               c.column_name AS c_column_name,
               c.data_type   AS c_data_type,
               c.comment     AS c_comment,
               c.sort_order  AS c_sort_order
        FROM   source_tables t
        JOIN   conversation_tables ct ON ct.table_id = t.id
        LEFT JOIN table_columns c ON c.table_id = t.id
        WHERE  ct.conversation_id = #{conversationId}
        ORDER  BY t.id, c.sort_order
    </select>

</mapper>
```

需要在 `SourceTableMapper.java` 中声明这两个方法：

```java
package com.ddh.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.agent.domain.model.table.SourceTable;
import com.ddh.agent.domain.model.table.TableWithColumns;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SourceTableMapper extends BaseMapper<SourceTable> {
    List<TableWithColumns> selectWithColumnsByProjectId(@Param("projectId") Long projectId);
    List<TableWithColumns> selectWithColumnsByConversationId(@Param("conversationId") Long conversationId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/ backend-java/src/main/resources/
git commit -m "feat(java): add MyBatis Plus mappers and SourceTable XML for join queries"
```

---

## Task 6：Repository 实现

**Files:**
- Create: `infrastructure/persistence/repository/UserRepositoryImpl.java`
- Create: `infrastructure/persistence/repository/ProjectRepositoryImpl.java`
- Create: `infrastructure/persistence/repository/SourceTableRepositoryImpl.java`
- Create: `infrastructure/persistence/repository/ConversationRepositoryImpl.java`
- Create: `infrastructure/persistence/repository/EtlRepositoryImpl.java`

- [ ] **Step 1: 创建 `UserRepositoryImpl.java`**

```java
package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import com.ddh.agent.infrastructure.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username)));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, email)));
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        return user;
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(null);
    }
}
```

- [ ] **Step 2: 创建 `ProjectRepositoryImpl.java`**

```java
package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.project.*;
import com.ddh.agent.infrastructure.persistence.mapper.ProjectMapper;
import com.ddh.agent.infrastructure.persistence.mapper.ProjectTableMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    @Autowired private ProjectMapper projectMapper;
    @Autowired private ProjectTableMapper projectTableMapper;

    @Override
    public Optional<Project> findById(Long id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    @Override
    public List<Project> findByOwnerId(Long ownerId) {
        return projectMapper.selectList(
            new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, ownerId)
                .orderByDesc(Project::getCreatedAt));
    }

    @Override
    public Project save(Project project) {
        if (project.getId() == null) {
            projectMapper.insert(project);
        } else {
            projectMapper.updateById(project);
        }
        return project;
    }

    @Override
    public void deleteById(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public List<ProjectTable> findTablesByProjectId(Long projectId) {
        return projectTableMapper.selectList(
            new LambdaQueryWrapper<ProjectTable>().eq(ProjectTable::getProjectId, projectId));
    }

    @Override
    public boolean existsProjectTable(Long projectId, Long tableId) {
        return projectTableMapper.selectCount(
            new LambdaQueryWrapper<ProjectTable>()
                .eq(ProjectTable::getProjectId, projectId)
                .eq(ProjectTable::getTableId, tableId)) > 0;
    }

    @Override
    public void addProjectTable(Long projectId, Long tableId) {
        ProjectTable pt = new ProjectTable();
        pt.setProjectId(projectId);
        pt.setTableId(tableId);
        projectTableMapper.insert(pt);
    }

    @Override
    public void removeProjectTable(Long projectId, Long tableId) {
        projectTableMapper.delete(
            new LambdaQueryWrapper<ProjectTable>()
                .eq(ProjectTable::getProjectId, projectId)
                .eq(ProjectTable::getTableId, tableId));
    }
}
```

- [ ] **Step 3: 创建 `SourceTableRepositoryImpl.java`**

```java
package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.infrastructure.persistence.mapper.SourceTableMapper;
import com.ddh.agent.infrastructure.persistence.mapper.TableColumnMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class SourceTableRepositoryImpl implements SourceTableRepository {

    @Autowired private SourceTableMapper sourceTableMapper;
    @Autowired private TableColumnMapper tableColumnMapper;

    @Override
    public Optional<SourceTable> findById(Long id) {
        return Optional.ofNullable(sourceTableMapper.selectById(id));
    }

    @Override
    public List<SourceTable> findVisible(Long currentUserId) {
        return sourceTableMapper.selectList(
            new LambdaQueryWrapper<SourceTable>()
                .eq(SourceTable::getScope, 1)
                .or()
                .eq(SourceTable::getScope, 2).eq(SourceTable::getOwnerId, currentUserId)
                .orderByDesc(SourceTable::getCreatedAt));
    }

    @Override
    public SourceTable save(SourceTable table) {
        if (table.getId() == null) {
            sourceTableMapper.insert(table);
        } else {
            sourceTableMapper.updateById(table);
        }
        return table;
    }

    @Override
    public void deleteById(Long id) {
        deleteColumnsByTableId(id);
        sourceTableMapper.deleteById(id);
    }

    @Override
    public List<TableColumn> findColumnsByTableId(Long tableId) {
        return tableColumnMapper.selectList(
            new LambdaQueryWrapper<TableColumn>()
                .eq(TableColumn::getTableId, tableId)
                .orderByAsc(TableColumn::getSortOrder));
    }

    @Override
    public void saveColumns(List<TableColumn> columns) {
        columns.forEach(tableColumnMapper::insert);
    }

    @Override
    public void deleteColumnsByTableId(Long tableId) {
        tableColumnMapper.delete(
            new LambdaQueryWrapper<TableColumn>().eq(TableColumn::getTableId, tableId));
    }

    @Override
    public List<TableWithColumns> findWithColumnsByProjectId(Long projectId) {
        return sourceTableMapper.selectWithColumnsByProjectId(projectId);
    }

    @Override
    public List<TableWithColumns> findWithColumnsByConversationId(Long conversationId) {
        return sourceTableMapper.selectWithColumnsByConversationId(conversationId);
    }
}
```

- [ ] **Step 4: 创建 `ConversationRepositoryImpl.java`**

```java
package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.infrastructure.persistence.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ConversationRepositoryImpl implements ConversationRepository {

    @Autowired private ConversationMapper conversationMapper;
    @Autowired private ConversationTableMapper conversationTableMapper;
    @Autowired private MessageMapper messageMapper;

    @Override
    public Optional<Conversation> findById(Long id) {
        return Optional.ofNullable(conversationMapper.selectById(id));
    }

    @Override
    public List<Conversation> findByProjectId(Long projectId) {
        return conversationMapper.selectList(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getProjectId, projectId)
                .orderByDesc(Conversation::getCreatedAt));
    }

    @Override
    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null) {
            conversationMapper.insert(conversation);
        } else {
            conversationMapper.updateById(conversation);
        }
        return conversation;
    }

    @Override
    public List<ConversationTable> findTablesByConversationId(Long conversationId) {
        return conversationTableMapper.selectList(
            new LambdaQueryWrapper<ConversationTable>()
                .eq(ConversationTable::getConversationId, conversationId));
    }

    @Override
    public void replaceConversationTables(Long conversationId, List<Long> tableIds) {
        conversationTableMapper.delete(
            new LambdaQueryWrapper<ConversationTable>()
                .eq(ConversationTable::getConversationId, conversationId));
        tableIds.forEach(tid -> {
            ConversationTable ct = new ConversationTable();
            ct.setConversationId(conversationId);
            ct.setTableId(tid);
            conversationTableMapper.insert(ct);
        });
    }

    @Override
    public List<Message> findMessagesByConversationId(Long conversationId) {
        return messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
    }

    @Override
    public Message saveMessage(Message message) {
        messageMapper.insert(message);
        return message;
    }
}
```

- [ ] **Step 5: 创建 `EtlRepositoryImpl.java`**

```java
package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.etl.*;
import com.ddh.agent.infrastructure.persistence.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class EtlRepositoryImpl implements EtlRepository {

    @Autowired private EtlJobMapper etlJobMapper;
    @Autowired private EtlStepMapper etlStepMapper;

    @Override
    public Optional<EtlJob> findJobById(Long id) {
        return Optional.ofNullable(etlJobMapper.selectById(id));
    }

    @Override
    public List<EtlJob> findJobsByProjectId(Long projectId) {
        return etlJobMapper.selectList(
            new LambdaQueryWrapper<EtlJob>()
                .eq(EtlJob::getProjectId, projectId)
                .orderByDesc(EtlJob::getCreatedAt));
    }

    @Override
    public EtlJob saveJob(EtlJob job) {
        if (job.getId() == null) {
            etlJobMapper.insert(job);
        } else {
            etlJobMapper.updateById(job);
        }
        return job;
    }

    @Override
    public List<EtlStep> findStepsByJobId(Long jobId) {
        return etlStepMapper.selectList(
            new LambdaQueryWrapper<EtlStep>()
                .eq(EtlStep::getJobId, jobId)
                .orderByAsc(EtlStep::getStepOrder));
    }

    @Override
    public Optional<EtlStep> findStepById(Long id) {
        return Optional.ofNullable(etlStepMapper.selectById(id));
    }

    @Override
    public EtlStep saveStep(EtlStep step) {
        etlStepMapper.insert(step);
        return step;
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/
git commit -m "feat(java): add repository implementations backed by MyBatis Plus"
```

---

## Task 7：基础设施配置类

**Files:**
- Create: `infrastructure/config/MybatisPlusConfig.java`
- Create: `infrastructure/config/CorsConfig.java`
- Create: `infrastructure/config/AppConfig.java`

- [ ] **Step 1: 创建 `MybatisPlusConfig.java`**

```java
package com.ddh.agent.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 2: 创建 `CorsConfig.java`**

```java
package com.ddh.agent.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:3000");
        config.setAllowCredentials(true);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **Step 3: 创建 `AppConfig.java`**

```java
package com.ddh.agent.infrastructure.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /** SSE Agent 任务线程池，固定 10 线程避免资源耗尽 */
    @Bean(name = "agentExecutor")
    public ExecutorService agentExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/
git commit -m "feat(java): add infrastructure config (MyBatisPlus, CORS, OkHttp, thread pool)"
```

---

## Task 8：JWT 安全层

**Files:**
- Create: `infrastructure/security/JwtUtil.java`
- Create: `infrastructure/security/JwtAuthFilter.java`
- Create: `infrastructure/security/UserDetailsServiceImpl.java`
- Create: `infrastructure/config/SecurityConfig.java`
- Create: `src/test/java/com/ddh/agent/infrastructure/security/JwtUtilTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ddh/agent/infrastructure/security/JwtUtilTest.java
package com.ddh.agent.infrastructure.security;

import com.ddh.agent.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtUtilTest {

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void generateAndValidate() {
        String token = jwtUtil.generateToken(42L);
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void invalidToken_returnsFalse() {
        assertThat(jwtUtil.validateToken("not.a.token")).isFalse();
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
mvn test -pl backend-java -Dtest=JwtUtilTest -q 2>&1 | Select-String -Pattern "FAIL|ERROR|Tests run"
```
Expected: 编译错误或测试失败（`JwtUtil` 不存在）

- [ ] **Step 3: 创建 `JwtUtil.java`**

```java
package com.ddh.agent.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.secret-key}")
    private String secretKey;

    @Value("${app.token-expire-minutes}")
    private int expireMinutes;

    public String generateToken(Long userId) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expireMinutes * 60_000L))
            .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
            .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(keyBytes))
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}
```

- [ ] **Step 4: 运行确认通过**

```powershell
mvn test -pl backend-java -Dtest=JwtUtilTest -q
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: 创建 `UserDetailsServiceImpl.java`**

```java
package com.ddh.agent.infrastructure.security;

import com.ddh.agent.domain.model.user.User;
import com.ddh.agent.domain.model.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.valueOf(userId))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return new org.springframework.security.core.userdetails.User(
            String.valueOf(user.getId()),
            user.getPasswordHash(),
            Collections.emptyList()
        );
    }
}
```

- [ ] **Step 6: 创建 `JwtAuthFilter.java`**

```java
package com.ddh.agent.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String userId = String.valueOf(jwtUtil.extractUserId(token));
                UserDetails ud = userDetailsService.loadUserByUsername(userId);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 7: 创建 `SecurityConfig.java`**

```java
package com.ddh.agent.infrastructure.config;

import com.ddh.agent.infrastructure.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
            .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

- [ ] **Step 8: 运行所有测试**

```powershell
cd backend-java && mvn test -q
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add JWT security layer (JwtUtil, JwtAuthFilter, SecurityConfig)"
```

---

**Plan A 完成。** 运行 `mvn spring-boot:run` 应能启动并连接 MySQL（需本地 `ddh_agent` 数据库存在）。继续执行 [Plan B — Auth / Table / Project / Conversation CRUD](2026-06-13-java-plan-b-crud.md)。
