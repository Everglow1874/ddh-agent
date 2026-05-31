# DDH Agent · 数仓 ETL 辅助开发平台 设计文档

**日期：** 2026-05-31  
**状态：** 待实现

---

## 1. 项目概述

DDH Agent 是一个面向数据团队的 ETL 作业辅助开发平台。用户将数仓原表结构（从 GaussDB 平台导出的 CSV 文件）导入平台后，可以创建需求项目、选定本次用到的原表，然后通过自然语言与 AI Agent 沟通取数需求。Agent 分析需求、确认目标表结构和 ETL 步骤计划，最终生成符合 GaussDB 语法的 SQL 文件和执行计划文档。

### 核心价值

- 无需直连数仓，基于导入的 Schema 信息即可辅助开发
- Agent 按需查询表结构（Tool Use），不把所有 Schema 塞进 prompt
- 关键节点（目标表结构、ETL 步骤）强制用户确认，避免误解需求
- 生成 GaussDB 语法的 SQL + Markdown 执行计划，可直接交付使用

---

## 2. 技术选型

| 层 | 技术 |
|---|---|
| 前端 | React 18 + Ant Design 5 |
| 后端 | FastAPI + Python 3.11+ |
| ORM | SQLAlchemy 2.x |
| 数据库 | MySQL 8.x（平台元数据） |
| AI | Claude API (claude-sonnet-4-6) / Qwen API (qwen-max)，config.yaml 切换 |
| 产出文件 | 本地文件系统，按项目目录组织 |
| 认证 | JWT Bearer Token |

### LLM 配置文件结构（config.yaml）

```yaml
llm:
  provider: claude   # 或 qwen
  claude:
    api_key: sk-ant-xxx
    model: claude-sonnet-4-6
  qwen:
    api_key: sk-xxx
    model: qwen-max
```

---

## 3. 数据模型

> **约束：不设外键约束，枚举值用 TINYINT 或 VARCHAR，关联关系在应用层维护。**

### 3.1 表清单

| 表名 | 说明 |
|------|------|
| users | 用户账号 |
| source_tables | 原表元数据（公共/私有） |
| table_columns | 原表字段定义 |
| projects | 需求项目 |
| project_tables | 项目与原表的 N:M 关联 |
| conversations | 对话会话 |
| messages | 对话消息记录 |
| etl_jobs | ETL 作业 |
| etl_steps | ETL 步骤（每步一个 SQL 文件） |

### 3.2 表结构

**users**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| username | VARCHAR(64) UNIQUE | |
| email | VARCHAR(128) UNIQUE | |
| password_hash | VARCHAR(256) | bcrypt |
| role | TINYINT DEFAULT 2 | 1=admin, 2=member |
| created_at | DATETIME | |

**source_tables**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| name | VARCHAR(128) | 表名 |
| description | TEXT | |
| scope | TINYINT DEFAULT 2 | 1=public, 2=private |
| owner_id | BIGINT | ref users.id，public 表可为 NULL |
| created_at | DATETIME | |

**table_columns**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| table_id | BIGINT | ref source_tables.id |
| column_name | VARCHAR(128) | |
| data_type | VARCHAR(64) | |
| comment | TEXT | |
| sort_order | INT DEFAULT 0 | 字段排列顺序 |

**projects**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| name | VARCHAR(256) | |
| description | TEXT | |
| owner_id | BIGINT | ref users.id |
| status | TINYINT DEFAULT 1 | 1=draft, 2=in_progress, 3=done |
| created_at | DATETIME | |

**project_tables**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| project_id | BIGINT | ref projects.id |
| table_id | BIGINT | ref source_tables.id |

**conversations**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| project_id | BIGINT | ref projects.id |
| state | TINYINT DEFAULT 1 | 1=gathering, 2=schema_confirm, 3=steps_confirm, 4=generating, 5=done |
| created_at | DATETIME | |

**messages**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| conversation_id | BIGINT | ref conversations.id |
| role | VARCHAR(20) | 'user' / 'assistant' / 'tool' |
| content | TEXT | |
| created_at | DATETIME | |

**etl_jobs**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| project_id | BIGINT | ref projects.id |
| target_table | VARCHAR(128) | 目标表名 |
| target_schema | JSON | 目标表字段定义 |
| plan_md_path | VARCHAR(512) | plan.md 文件路径 |
| created_at | DATETIME | |

**etl_steps**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| job_id | BIGINT | ref etl_jobs.id |
| step_order | INT | 执行顺序 |
| step_name | VARCHAR(256) | 步骤描述 |
| is_temp_table | TINYINT(1) DEFAULT 0 | 0=最终表, 1=临时表 |
| sql_file_path | VARCHAR(512) | .sql 文件路径 |

---

## 4. Agent 工作流

### 4.1 对话状态机

```
gathering(1) -> schema_confirm(2) -> steps_confirm(3) -> generating(4) -> done(5)
```

每个状态下的 UI 行为：

| state | 前端表现 |
|-------|----------|
| 1 gathering | 普通聊天气泡，输入框可用 |
| 2 schema_confirm | 显示目标表结构确认卡片，等待用户点击确认/修改 |
| 3 steps_confirm | 显示 ETL 步骤���认卡片，等待用户点击确认/修改 |
| 4 generating | 右侧面板逐步显示 SQL，进度条 |
| 5 done | 右侧面板完整展示 SQL + plan.md，下载按钮激活 |

### 4.2 Agent 工具集

```python
tools = [
    list_project_tables(project_id: int) -> list[TableMeta]
    # 返回本次项目勾选的所有原表名称和 ID

    get_table_schema(table_id: int) -> TableSchema
    # 返回指定表的字段列表（column_name, data_type, comment）

    get_conversation_state(conversation_id: int) -> int
    # 返回当前对话阶段
]
```

### 4.3 SSE 事件类型

| event type | 触发时机 | payload |
|------------|----------|---------|
| `token` | Agent 流式输出文字 | `{"text": "..."}` |
| `schema_proposal` | Agent 确定目标表结构 | `{"columns": [...]}` |
| `steps_proposal` | Agent 规划 ETL 步骤 | `{"steps": [...]}` |
| `step_generated` | 单步 SQL 生成完毕 | `{"step_order": 1, "file": "step1_xxx.sql"}` |
| `done` | 全部生成完毕 | `{"job_id": 123}` |
| `error` | 异常 | `{"message": "..."}` |

### 4.4 确认节点处理

**用户点击"确认"：**
- 前端 POST `/api/conversations/{id}/confirm-schema` 或 `confirm-steps`
- 后端更新 `conversations.state`，Agent 继续下一阶段

**用户点击"修改"：**
- 用户输入修改意见，作为新的 user 消息进入对话
- `conversations.state` 回退到上一阶段，Agent 重新分析

---

## 5. API 接口设计

所有接口前缀 `/api`，认证方式 JWT Bearer Token（login/register 除外）。

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /auth/register | 注册 |
| POST | /auth/login | 登录，返回 JWT |
| POST | /auth/logout | 登出 |

### 原表仓库
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /tables | 列表（?scope=public\|private\|all） |
| POST | /tables/import | 上传 CSV，multipart/form-data |
| GET | /tables/{id} | 表详情 + 字段列表 |
| PUT | /tables/{id} | 更新表名/描述 |
| DELETE | /tables/{id} | 删除表 |

CSV 导入字段格式（每行一个字段）：

| CSV 列 | 说明 |
|--------|------|
| column_name | 字段名（必填） |
| data_type | 字段类型（必填） |
| comment | 字段注释（可选） |

### 需求项目
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /projects | 我的项目列表 |
| POST | /projects | 新建项目 |
| GET | /projects/{id} | 项目详情 |
| PUT | /projects/{id} | 更新项目 |
| DELETE | /projects/{id} | 删除项目 |
| POST | /projects/{id}/tables | 批量关联原表 |
| DELETE | /projects/{id}/tables/{tid} | 移除关联 |

### Agent 对话
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /projects/{id}/conversations | 新建对话 |
| GET | /projects/{id}/conversations | 对话历史列表 |
| POST | /conversations/{id}/chat | 发送消息（触发 Agent） |
| GET | /conversations/{id}/stream | SSE 流式输出 |
| POST | /conversations/{id}/confirm-schema | 确认目标表结构 |
| POST | /conversations/{id}/confirm-steps | 确认 ETL 步骤计划 |
| GET | /conversations/{id}/messages | 历史消息列表 |

### ETL 作业
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /projects/{id}/jobs | 项目下所有作业 |
| GET | /jobs/{id} | 作业详情 + 步骤列表 |
| GET | /jobs/{id}/steps/{sid}/sql | 单步 SQL 内容 |
| GET | /jobs/{id}/download | 下载全部文件（ZIP） |

### 系统配置（admin 权限）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /admin/config | 查看当前 LLM 配置 |
| PUT | /admin/config | 切换 provider 或更新 key |

---

## 6. 前端页面结构

### 6.1 整体布局

固定 TopBar 导航：原表仓库 / 我的项目 / 系统配置（admin）/ 用户菜单

### 6.2 页面列表

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录 | /login | JWT 登录 |
| 注册 | /register | 账号注册 |
| 原表仓库 | /tables | 左侧公共/私有分类，右侧表格列表 + CSV 导入 |
| 项目列表 | /projects | 卡片式，显示状态/原表/时间 |
| 项目详情 | /projects/:id | Tab 切换：对话 / ETL 作业记录 |
| Agent 对话 | /projects/:id/chat | 三栏布局（见 6.3） |
| 系统配置 | /admin/config | LLM provider 切换 |

### 6.3 Agent 对话页三栏布局

```
+----------------------------------------------------------+
|  TopBar                                                    |
+--------------+--------------------------+-----------------+
|  对话历史     |  聊天区��SSE 流式气泡）    |  SQL 结果面板   |
|  + 新��对话   |  + 确认卡片              |  + 下载按钮     |
|              |  + 输入框                |                 |
+--------------+--------------------------+-----------------+
```

- **确认卡片**：金色边框卡片，state=2/3 时替代普通气泡出现
- **SQL 结果面板**：state=4/5 时激活，代码高亮 + Tab 切换 SQL 步骤 / plan.md

### 6.4 视觉风格（晴空白主题）

- 背景：`#f8faff`（页面）/ `#ffffff`（卡片/侧边栏）
- 主色：`#4361ee`（蓝紫）
- 边框：`#e8eef8`
- 文字：`#1a2a4a`（主）/ `#8a9ab8`（次）
- 确认卡片：`#fffbec` 背景，`#f0c040` 边框
- 用户气泡：`#4361ee` 背景白字；Agent 气泡：白色背景灰字

---

## 7. 文件产出结构

```
projects/
+-- {project_id}/
    +-- step1_{temp_table_name}.sql    # 临时表步骤
    +-- step2_{target_table_name}.sql  # 最终目标表步骤
    +-- plan.md                        # 执行计划说明文档
```

plan.md 内容结构：
- 需求描述
- 目标表结构
- 各步骤说明（步骤名、输入输出表、处理逻辑）
- 执行顺序

---

## 8. 错误处理

| 场景 | 处理方式 |
|------|----------|
| LLM API 超时 / 错误 | SSE 推送 `error` 事件，前端提示可重试 |
| CSV 格式不符 | 导入时返回 400，提示缺少哪些列 |
| 对话状态不一致 | 后端校验 state 合法性，拒绝越阶操作 |
| 文件写入失败 | 回滚 etl_jobs / etl_steps 记录，SSE 推送 error |
| JWT 过期 | 返回 401，前端跳转登录页 |
