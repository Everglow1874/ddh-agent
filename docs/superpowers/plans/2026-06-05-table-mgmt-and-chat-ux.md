# 表管理增强 + 对话体验修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ddh-agent 平台上增量实现表结构编辑、表关联关系、项目选中表预览三个功能，并修复对话流式 Markdown 渲染与对话记录丢失两个 Bug。

**Architecture:** 后端沿用 FastAPI + SQLAlchemy 既有模式（service 层 + router 层，无外键约束，枚举用整数，PK 用 `BigInteger().with_variant(Integer, "sqlite")`）。前端沿用 React + Ant Design + 既有 api 客户端层。任务粒度按模块划分，每个 Task 完整实现一个模块及其测试，不逐步拆分。

**Tech Stack:** FastAPI, SQLAlchemy 2.x, pytest；React 18, Ant Design 5, react-markdown, react-syntax-highlighter, Vitest

**Spec:** `docs/superpowers/specs/2026-06-05-table-mgmt-and-chat-ux-design.md`

**测试运行：** 后端 `cd backend && ../.venv/Scripts/python -m pytest tests/ -q`；前端 `cd frontend && npm test`

---

## Task 1：后端 — 表关联模型 + 字段替换/关系/项目表接口（F1+F2+F3 数据与 API）

完整实现这三个功能的后端数据层与接口，含测试。

**Files:**
- Create: `backend/app/models/table_relation.py`
- Modify: `backend/app/models/__init__.py`（注册新模型）
- Modify: `backend/app/schemas/source_table.py`（加 `ColumnIn`）
- Create: `backend/app/schemas/relation.py`
- Modify: `backend/app/services/table_service.py`（加 `replace_columns`、`list_relations_for_table`、`create_relation`、`delete_relation`、`get_project_tables_with_relations`）
- Modify: `backend/app/routers/tables.py`（加 `PUT /tables/{id}/columns`、`GET /tables/{id}/relations`）
- Create: `backend/app/routers/relations.py`（`POST /relations`、`DELETE /relations/{id}`）
- Modify: `backend/app/routers/projects.py`（加 `GET /projects/{id}/tables`）
- Modify: `backend/app/main.py`（挂载 relations 路由）
- Create: `backend/tests/test_relations.py`
- Modify: `backend/tests/test_tables.py`（加字段替换测试）

### 实现要点

**模型 `table_relation.py`**（照搬 source_table.py 的 PK 模式）：
```python
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy import BigInteger, String, DateTime, Integer
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class TableRelation(Base):
    __tablename__ = "table_relations"

    id: Mapped[int] = mapped_column(BigInteger().with_variant(Integer, "sqlite"), primary_key=True, autoincrement=True)
    from_table_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    from_column: Mapped[str] = mapped_column(String(128), nullable=False)
    to_table_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    to_column: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(String(256), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=lambda: datetime.now(timezone.utc))
```
在 `models/__init__.py` 中 `from app.models.table_relation import TableRelation` 并加入 `__all__`。

**schema：** `source_table.py` 加：
```python
class ColumnIn(BaseModel):
    column_name: str
    data_type: str
    comment: Optional[str] = None
```
`relation.py`：
```python
from pydantic import BaseModel
from typing import Optional


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

**service（table_service.py 追加）：**
```python
from app.models.table_relation import TableRelation
from app.models.project import ProjectTable

def replace_columns(db, table_id: int, columns: list[dict]) -> None:
    """整体替换某表的字段，列表顺序即 sort_order。"""
    db.query(TableColumn).filter(TableColumn.table_id == table_id).delete()
    for i, col in enumerate(columns):
        db.add(TableColumn(
            table_id=table_id,
            column_name=col["column_name"],
            data_type=col["data_type"],
            comment=col.get("comment"),
            sort_order=i,
        ))
    db.commit()

def list_relations_for_table(db, table_id: int) -> list[TableRelation]:
    return db.query(TableRelation).filter(
        (TableRelation.from_table_id == table_id) | (TableRelation.to_table_id == table_id)
    ).all()

def create_relation(db, **kwargs) -> TableRelation:
    rel = TableRelation(**kwargs)
    db.add(rel)
    db.commit()
    db.refresh(rel)
    return rel

def delete_relation(db, relation_id: int) -> bool:
    rel = db.query(TableRelation).filter(TableRelation.id == relation_id).first()
    if rel is None:
        return False
    db.delete(rel)
    db.commit()
    return True

def get_project_tables_with_relations(db, project_id: int) -> dict:
    """本项目勾选的表（含字段）+ 两端都在勾选集合内的关系。"""
    rows = db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
    table_ids = [r.table_id for r in rows]
    tables = [get_table_with_columns(db, tid) for tid in table_ids]
    tables = [t for t in tables if t is not None]
    relations = db.query(TableRelation).filter(
        TableRelation.from_table_id.in_(table_ids),
        TableRelation.to_table_id.in_(table_ids),
    ).all() if table_ids else []
    return {"tables": tables, "relations": relations}
```

**路由：**
- `tables.py` 加：
```python
from app.schemas.source_table import ColumnIn
from app.schemas.relation import RelationOut
from app.services.table_service import replace_columns, list_relations_for_table

@router.put("/{table_id}/columns", response_model=TableDetailOut)
def update_columns(table_id: int, columns: list[ColumnIn],
                   db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
    if table is None:
        raise HTTPException(status_code=404, detail="Table not found")
    replace_columns(db, table_id, [c.model_dump() for c in columns])
    return get_table_with_columns(db, table_id)

@router.get("/{table_id}/relations", response_model=list[RelationOut])
def get_relations(table_id: int, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)):
    return list_relations_for_table(db, table_id)
```
- 新 `relations.py`：
```python
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.models.user import User
from app.schemas.relation import RelationOut, RelationIn
from app.services.table_service import create_relation, delete_relation

router = APIRouter(prefix="/relations", tags=["relations"])

@router.post("", response_model=RelationOut, status_code=201)
def create(body: RelationIn, db: Annotated[Session, Depends(get_db)],
           current_user: Annotated[User, Depends(get_current_user)]):
    return create_relation(db, from_table_id=body.from_table_id, from_column=body.from_column,
                           to_table_id=body.to_table_id, to_column=body.to_column, description=body.description)

@router.delete("/{relation_id}", status_code=204)
def delete(relation_id: int, db: Annotated[Session, Depends(get_db)],
           current_user: Annotated[User, Depends(get_current_user)]):
    if not delete_relation(db, relation_id):
        raise HTTPException(status_code=404, detail="Relation not found")
```
- `projects.py` 加（复用 `_get_owned_project`）：
```python
from app.services.table_service import get_project_tables_with_relations

@router.get("/{project_id}/tables")
def get_project_tables(project_id: int, db: Annotated[Session, Depends(get_db)],
                       current_user: Annotated[User, Depends(get_current_user)]):
    _get_owned_project(project_id, db, current_user)
    return get_project_tables_with_relations(db, project_id)
```
- `main.py` 加 `from app.routers import ..., relations` 与 `app.include_router(relations.router, prefix="/api")`。

### 测试（覆盖即可，不逐字段断言）

`test_tables.py` 加：用已导入的表，`PUT /api/tables/{id}/columns` 传 3 个字段 → 200，再 GET 详情确认字段数=3、顺序正确；传字段含中文注释确认往返正常。

`test_relations.py`（用 conftest 的 `client` + `auth_headers` + `db_session`）：
- 导入两张表 → `POST /api/relations` 建关系 → 201
- `GET /api/tables/{id}/relations` 返回该关系
- `DELETE /api/relations/{id}` → 204；再删不存在的 → 404
- 建项目关联两表 + 一条关系 → `GET /api/projects/{pid}/tables` 返回 tables 含字段、relations 含该关系
- 关系一端不在项目勾选集合时，`GET /api/projects/{pid}/tables` 的 relations 不含它

**验证：** `pytest tests/test_tables.py tests/test_relations.py -q` 全绿；再跑全量 `pytest tests/ -q`。

**Commit:** `feat: backend table column editing, relations, project tables endpoint`

---

## Task 2：后端 — Agent 集成关联关系 + 修复对话被吞（F2 落地 + B2）

**Files:**
- Modify: `backend/app/services/agent_tools.py`（`list_project_tables` 返回值带关系）
- Modify: `backend/app/services/agent_service.py`（system prompt 加关系指引；Bug2 修复）
- Modify: `backend/tests/test_agent_tools.py`、`backend/tests/test_agent_service.py`

### 实现要点

**agent_tools.py** — `execute_tool` 的 `list_project_tables` 分支，返回值除 tables 外加 relations：
```python
if name == "list_project_tables":
    project_id = input_data["project_id"]
    rows = db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
    table_ids = [r.table_id for r in rows]
    tables = db.query(SourceTable).filter(SourceTable.id.in_(table_ids)).all()
    from app.models.table_relation import TableRelation
    relations = []
    if table_ids:
        relations = db.query(TableRelation).filter(
            TableRelation.from_table_id.in_(table_ids),
            TableRelation.to_table_id.in_(table_ids),
        ).all()
    return {
        "tables": [{"id": t.id, "name": t.name, "scope": t.scope} for t in tables],
        "relations": [
            {"from_table_id": r.from_table_id, "from_column": r.from_column,
             "to_table_id": r.to_table_id, "to_column": r.to_column}
            for r in relations
        ],
    }
```

**agent_service.py** — `build_system_prompt(state=1)` 末尾追加一句：
```
"如果工具返回了表关联关系（relations），生成 SQL 时优先使用这些字段对作为 JOIN 条件，不要臆造关联键。"
```

**Bug2 修复** — `_run_agent_sync` 的 tool-use 循环，把 assistant 文字保存移到「只要有文字就保存」：
```python
while True:
    response = provider.chat_with_tools(messages=messages, tools=tools, system=system)
    if response.text:
        emit({"type": "token", "text": response.text})
        _save_message(db, conversation_id, "assistant", response.text)   # 任何回合都保存
    if response.stop_reason == "end_turn":
        break                                                            # 删除原 end_turn 里的重复保存
    messages.append(response.to_assistant_message())
    for tool_call in response.tool_calls:
        result = execute_tool(tool_call.name, tool_call.input, db, conversation, emit)
        if tool_call.name == "generate_sql" and result.get("status") == "sql_saved":
            generated_steps.append(result)
        messages.append({"role": "tool_result", "tool_call_id": tool_call.id, "content": json.dumps(result)})
```

### 测试

- `test_agent_tools.py` 加：建项目+两表+一条关系，调 `execute_tool("list_project_tables", ...)`，断言返回含 `relations` 且长度=1、字段对正确。
- `test_agent_service.py` 加：用 MockLLMProvider 模拟「先返回带文字的 tool_use 回合（调一个工具）→ 再返回 end_turn 文字」，跑完 `run_and_stream` 后查 DB，断言两条 assistant 文字都被持久化（验证 Bug2 修复）。MockLLMProvider 已存在于该测试文件。
- `build_system_prompt(1)` 断言包含 "JOIN" 或 "关联"。

**验证：** `pytest tests/test_agent_tools.py tests/test_agent_service.py -q` 全绿；全量 `pytest tests/ -q`。

**Commit:** `feat: agent uses table relations for joins; fix swallowed assistant messages`

---

## Task 3：前端 — API 客户端 + 类型扩展

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/tables.ts`
- Modify: `frontend/src/api/projects.ts`

### 实现要点

`types.ts` 加：
```typescript
export interface TableRelation {
  id: number;
  from_table_id: number;
  from_column: string;
  to_table_id: number;
  to_column: string;
  description: string | null;
}

export interface ColumnInput {
  column_name: string;
  data_type: string;
  comment?: string | null;
}

export interface ProjectTablesResult {
  tables: TableDetail[];
  relations: TableRelation[];
}
```

`tables.ts` 加：
```typescript
import type { TableDetail, TableRelation, ColumnInput } from "./types";

export async function updateColumns(tableId: number, columns: ColumnInput[]): Promise<TableDetail> {
  const resp = await client.put<TableDetail>(`/tables/${tableId}/columns`, columns);
  return resp.data;
}

export async function getRelations(tableId: number): Promise<TableRelation[]> {
  const resp = await client.get<TableRelation[]>(`/tables/${tableId}/relations`);
  return resp.data;
}

export async function createRelation(body: {
  from_table_id: number; from_column: string;
  to_table_id: number; to_column: string; description?: string | null;
}): Promise<TableRelation> {
  const resp = await client.post<TableRelation>("/relations", body);
  return resp.data;
}

export async function deleteRelation(relationId: number): Promise<void> {
  await client.delete(`/relations/${relationId}`);
}
```

`projects.ts` 加：
```typescript
import type { ProjectTablesResult } from "./types";

export async function getProjectTables(projectId: number): Promise<ProjectTablesResult> {
  const resp = await client.get<ProjectTablesResult>(`/projects/${projectId}/tables`);
  return resp.data;
}
```

**验证：** `npm test`（不应破坏现有测试）；`npm run build` 编译通过。

**Commit:** `feat(frontend): api client for column editing, relations, project tables`

---

## Task 4：前端 — 表详情抽屉（字段编辑 + 关联管理）（F1+F2 UI）

**Files:**
- Create: `frontend/src/pages/tables/TableDetailDrawer.tsx`
- Modify: `frontend/src/pages/TablesPage.tsx`（点表名打开抽屉）
- Create: `frontend/src/pages/tables/TableDetailDrawer.test.tsx`

### 实现要点

`TableDetailDrawer` props：`tableId: number | null`、`open: boolean`、`onClose: () => void`、`allTables: SourceTable[]`（供关联选目标表）。打开时 `getTable(tableId)` 拉详情、`getRelations(tableId)` 拉关系。

**字段区**：Ant Design `Table` 可编辑，本地维护 `columns: ColumnInput[]` state：
- 每行三列：字段名、类型、注释，均为可编辑 `Input`
- 「+ 添加字段」追加空行；每行「删除」按钮移除
- 排序：用上移/下移按钮调整（简化实现，不引入拖拽库）
- 「保存字段」按钮 → `updateColumns(tableId, columns)`，成功 message 提示

**关联区**：
- 列出已有关系：`{from_column} → {目标表名}.{to_column}`，每条「删除」→ `deleteRelation`
- 「+ 添加关联」表单：本表字段下拉（来自详情 columns）、目标表下拉（allTables 排除自己）、目标字段输入、可选说明 → `createRelation` 后刷新关系列表

`TablesPage.tsx`：表名列改为可点击，点击 `setDrawerTableId(record.id); setDrawerOpen(true)`，渲染 `<TableDetailDrawer .../>`，传入当前 `tables` 作为 allTables。

### 测试

`TableDetailDrawer.test.tsx`（mock `../../api/tables`）：
- mock `getTable` 返回带 2 字段的详情、`getRelations` 返回 []
- 渲染（open=true），断言两个字段名出现在文档
- 点「+ 添加字段」后行数+1
- 断言「保存字段」按钮存在

**验证：** `npm test`；`npm run build`。

**Commit:** `feat(frontend): table detail drawer with column editing and relation management`

---

## Task 5：前端 — 对话页 DeepSeek 风重构 + Markdown 流式 + 数据预览面板（B1+F3+视觉）

**Files:**
- Create: `frontend/src/pages/chat/MarkdownMessage.tsx`（助手消息 Markdown 渲染 + 代码块复制）
- Create: `frontend/src/pages/chat/DataPreviewPanel.tsx`（左栏数据预览）
- Modify: `frontend/src/pages/ChatPage.tsx`（DeepSeek 布局 + 用 MarkdownMessage + 接入预览面板）
- Create: `frontend/src/pages/chat/MarkdownMessage.test.tsx`

### 实现要点

**`MarkdownMessage.tsx`**：用 `react-markdown` 渲染，`components` 覆盖 `code`，块级代码用 `react-syntax-highlighter`（Prism）+ 顶部语言标签 + 右上角「复制」按钮（`navigator.clipboard.writeText`）。行内代码普通样式。
```typescript
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { message } from "antd";

export function MarkdownMessage({ content }: { content: string }) {
  return (
    <ReactMarkdown
      components={{
        code({ inline, className, children, ...props }: any) {
          const match = /language-(\w+)/.exec(className || "");
          const text = String(children).replace(/\n$/, "");
          if (inline) return <code className={className} {...props}>{children}</code>;
          const lang = match?.[1] || "text";
          return (
            <div style={{ position: "relative", margin: "8px 0" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center",
                            background: "#1e1e2e", color: "#888", fontSize: 12, padding: "4px 10px",
                            borderTopLeftRadius: 6, borderTopRightRadius: 6 }}>
                <span>{lang}</span>
                <a onClick={() => { navigator.clipboard.writeText(text); message.success("已复制"); }}
                   style={{ cursor: "pointer", color: "#aaa" }}>复制</a>
              </div>
              <SyntaxHighlighter language={lang} customStyle={{ margin: 0, fontSize: 13,
                                  borderBottomLeftRadius: 6, borderBottomRightRadius: 6 }}>
                {text}
              </SyntaxHighlighter>
            </div>
          );
        },
      }}
    >
      {content}
    </ReactMarkdown>
  );
}
```

**`DataPreviewPanel.tsx`**：props `projectId: number`。挂载时 `getProjectTables(projectId)`。用 Ant `Collapse` 列出每张表，展开看字段（名/类型/注释小表格）；底部小节列出 relations（`A.x ↔ B.y`，表名用 id 映射到 name）。

**`ChatPage.tsx` 改造**：
- 左栏 `Sider` 内：上半「对话历史」（现有 ConversationSidebar），下半放可折叠的 `<DataPreviewPanel projectId={projectId}/>`（用 Ant `Collapse` 包一层，标题「数据预览」）。
- 中栏对话区：外层加居中容器（`maxWidth: 760, margin: "0 auto"`）。
  - 用户消息：浅灰圆角气泡右对齐（`background:#f0f2f8; color:#1a2a4a; borderRadius:12`）
  - 助手消息：**无气泡**，左侧小图标 + `<MarkdownMessage content={b.content}/>`，背景透明
  - 流式中（streaming 且最后一条是 assistant）末尾显示闪烁光标：一个 `▍` 用 CSS animation 闪烁
  - 助手消息底部「复制」按钮（复制纯文本）
- 确认卡片、SQL 结果面板、输入框逻辑保持不变。

闪烁光标 CSS（可放组件内 `<style>` 或内联 keyframes，简化：用一个会闪的 span）。

### 测试

`MarkdownMessage.test.tsx`：
- 渲染 `content="# 标题\n\n- 项目1\n- 项目2"`，断言「标题」「项目1」出现
- 渲染含 ```sql 代码块```，断言「复制」按钮出现、SQL 文本出现

（ChatPage 已有测试 mock 了 conversations/sse；本任务新增 `getProjectTables` 调用，需在 ChatPage.test.tsx 里 mock `../api/projects` 的 `getProjectTables` 返回 `{tables:[],relations:[]}`，避免现有测试因未 mock 而报错——一并修改 ChatPage.test.tsx。）

**验证：** `npm test` 全绿；`npm run build` 通过；手动起前后端在浏览器确认对话 Markdown 渲染与数据预览（可选）。

**Commit:** `feat(frontend): DeepSeek-style chat with live markdown + data preview panel`

---

## Self-Review

**Spec 覆盖：**
- F1 字段读写 → Task 1（后端 PUT columns）+ Task 4（前端抽屉）✅
- F2 表关联 → Task 1（模型+CRUD）+ Task 2（Agent 用）+ Task 4（前端管理）✅
- F3 选中表预览 → Task 1（GET project tables）+ Task 5（预览面板）✅
- B1 Markdown 流式 → Task 5 ✅
- B2 对话被吞 → Task 2 ✅

**类型一致性：** `ColumnIn`(后端)/`ColumnInput`(前端)、`RelationOut`/`TableRelation`、`get_project_tables_with_relations` 返回 `{tables, relations}` 与前端 `ProjectTablesResult` 对应、`updateColumns` 路径 `/tables/{id}/columns` 与后端一致。✅

**新增表需建表：** 后端启动时 `Base.metadata.create_all` 会自动建 `table_relations`（已在 main.py lifespan），无需手动迁移；测试用 SQLite 内存库同样自动建。✅

**不在范围：** 确认卡片/SQL 刷新重现、关系基数、ER 图、重新生成按钮。
