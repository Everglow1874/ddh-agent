# 表管理增强 + 对话体验修复 设计文档

**日期：** 2026-06-05
**状态：** 待实现
**基线：** 在已上线的 ddh-agent 平台（后端 FastAPI + 前端 React/AntD）之上增量扩展

---

## 1. 目标

三个新功能 + 两个 Bug 修复：

| # | 类型 | 内容 |
|---|------|------|
| F1 | 功能 | 原表仓库支持表结构（字段）的阅读与修改（完整增删改字段） |
| F2 | 功能 | 维护表之间的字段级关联关系，Agent 生成 SQL 时据此自动 JOIN |
| F3 | 功能 | 项目辅助开发时，对话页可预览本次勾选的表结构与关联 |
| B1 | Bug | AI 流式响应实时渲染为 Markdown（DeepSeek 风格），不再显示原始文本 |
| B2 | Bug | 对话记录被吞：Agent 工具回合的解释文字未入库，刷新后丢失 |

---

## 2. 数据模型

### 新增表 `table_relations`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| from_table_id | BIGINT | 关联起始表（如事实表），ref source_tables.id |
| from_column | VARCHAR(128) | 起始字段名（如 student_id） |
| to_table_id | BIGINT | 关联目标表（如维度表），ref source_tables.id |
| to_column | VARCHAR(128) | 目标字段名（如 student_id） |
| description | VARCHAR(256) | 可选说明 |
| created_at | DATETIME | |

约束：沿用项目规范——**无外键约束**，关联仅应用层维护。

设计取舍：不记录 cardinality（1:N / N:1）——YAGNI，Agent 生成 JOIN 只需知道"哪两个字段相等"。`from`/`to` 仅表示成对关系，JOIN 时无方向性差异。

---

## 3. 后端

### 3.1 字段编辑（F1）

新增 schema `ColumnIn`（用于整体替换）：
```python
class ColumnIn(BaseModel):
    column_name: str
    data_type: str
    comment: Optional[str] = None
```

新增端点：
| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | /tables/{id}/columns | 传入完整有序字段列表，整体替换。列表顺序即 sort_order |

`PUT /tables/{id}`（已存在，改表名/描述）保持不变。

整体替换语义：删除该表所有 `table_columns` 记录，按传入列表顺序重建（sort_order = 索引）。简单、幂等、一次支持增/删/改/排序。

### 3.2 表关联关系（F2）

新增 model `TableRelation`（见 §2），新增 schema：
```python
class RelationOut(BaseModel):
    id: int
    from_table_id: int
    from_column: str
    to_table_id: int
    to_column: str
    description: Optional[str]
    model_config = {"from_attributes": True}

class RelationIn(BaseModel):
    from_table_id: int
    from_column: str
    to_table_id: int
    to_column: str
    description: Optional[str] = None
```

新增端点：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /tables/{id}/relations | 查 from_table_id 或 to_table_id = id 的所有关系 |
| POST | /relations | 创建关系 |
| DELETE | /relations/{id} | 删除关系 |

### 3.3 项目选中表预览（F3）

新增端点：
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /projects/{id}/tables | 返回本项目勾选的所有原表（含字段）+ 这些表之间的关联关系 |

响应结构：
```json
{
  "tables": [
    {"id": 1, "name": "fact_exam_score", "description": "...", "columns": [...]}
  ],
  "relations": [
    {"id": 1, "from_table_id": 1, "from_column": "student_id",
     "to_table_id": 2, "to_column": "student_id", "description": null}
  ]
}
```
relations 仅包含两端都在本项目勾选范围内的关系。

### 3.4 Agent 改造（F2 落地）

- `agent_tools.execute_tool` 的 `list_project_tables` 分支：返回值除 tables 外，**额外带上选中表之间的关联关系**（查 project_tables → 所有 table_id → table_relations 中两端都在集合内的记录）。
- `agent_service.build_system_prompt(state=1)`：增加指引——"当已知表关联关系时，生成 SQL 时优先使用这些字段对作为 JOIN 条件，不要臆造关联键。"

### 3.5 Bug2 修复（对话被吞）

`agent_service._run_agent_sync` 当前仅在 `stop_reason == "end_turn"` 时 `_save_message(assistant)`。修改为：**任何回合只要 `response.text` 非空，就先持久化为 assistant 消息**，再处理工具调用。

修改后逻辑：
```python
while True:
    response = provider.chat_with_tools(...)
    if response.text:
        emit({"type": "token", "text": response.text})
        _save_message(db, conversation_id, "assistant", response.text)  # 移到这里，覆盖工具回合
    if response.stop_reason == "end_turn":
        break
    messages.append(response.to_assistant_message())
    for tool_call in response.tool_calls:
        ...
```
注意：去掉原 end_turn 分支里的重复 `_save_message`，避免重复保存。

> 范围说明：本次仅修复"助手文字被吞"。确认卡片/已生成 SQL 在刷新后重现属于更大改动（需把 proposal/step 持久化为结构化消息），不在本次范围。

---

## 4. 前端

### 4.1 表详情抽屉（F1 + F2）

原表仓库（TablesPage）表格行点击表名 → 打开 **表详情抽屉（Drawer）**，含两区：

**字段区**（F1）
- 可编辑表格：每行一个字段（字段名 / 类型 / 注释），支持：
  - 加行（新增字段）
  - 删行
  - 单元格编辑
  - 拖动排序
- 底部「保存」按钮 → `PUT /tables/{id}/columns`（传完整列表）

**关联关系区**（F2）
- 列出该表已有关联（from/to 表与字段）
- 「+ 添加关联」：选择本表字段 → 目标表 → 目标表字段 → 提交 `POST /relations`
- 每条可删除 `DELETE /relations/{id}`

### 4.2 对话页数据预览面板（F3）

对话页左栏（现为对话历史）增加**可折叠「数据预览」区**：
- 进入对话页时调 `GET /projects/{id}/tables`
- 列出本次勾选的表，点击展开看字段（名/类型/注释）
- 底部小区域列出表间关联关系（A.x ↔ B.y）

### 4.3 对话页 DeepSeek 风格重构（B1 + 视觉）

**布局**
- 中间对话区改为**居中窄栏**（max-width 720–800px），留白充足
- 三栏结构保留：左（对话历史 + 数据预览）｜ 中（对话）｜ 右（SQL 结果）

**消息样式**
- **助手消息：无气泡**，以干净 Markdown 文档形式渲染在白色背景上，左侧带小 Agent 图标
- **用户消息：浅灰圆角气泡，右对齐**

**Markdown 渲染**（B1 核心）
- 用 `react-markdown` 实时逐字渲染：标题、列表、表格、引用
- **代码块**：`react-syntax-highlighter` 语法高亮 + 顶部语言标签 + 右上角「复制」按钮
- 流式输出时末尾显示**闪烁光标**，结束后消失

**配色**
- 晴空白基调，对话区背景纯白、文字深灰 `#1a2a4a`、强调色主蓝 `#4361ee`

**助手消息底部**：「复制」按钮

确认卡片（SchemaConfirmCard / StepsConfirmCard）保留金色边框样式不变。

---

## 5. 错误处理

| 场景 | 处理 |
|------|------|
| PUT columns 传空列表 | 允许（表可暂时无字段），前端保存前给「字段为空」提示但不阻止 |
| POST relation 引用不存在的表/字段 | 不做强校验（无外键），仅存储；前端从已有字段下拉选，规避错填 |
| DELETE relation 不存在 | 返回 404 |
| GET /projects/{id}/tables 非本人项目 | 403（沿用 _get_owned_project 模式） |

---

## 6. 测试

- 后端：字段整体替换、关系 CRUD、项目选中表预览（含关系过滤）、`list_project_tables` 带关系、Bug2 的工具回合消息持久化
- 前端：表详情抽屉字段编辑、关联添加、数据预览面板加载、Markdown 助手气泡渲染（含代码块）

---

## 7. 不在本次范围

- 确认卡片 / 已生成 SQL 在对话刷新后的重现（需结构化消息持久化）
- 关系基数（1:N 等）
- 表关联的可视化 ER 图
- 「重新生成」按钮
