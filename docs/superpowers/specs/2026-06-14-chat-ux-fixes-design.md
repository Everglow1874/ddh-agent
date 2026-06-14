# 对话页四项问题修复 — 设计文档

日期：2026-06-14
范围：`backend-java`（Spring Boot + MyBatis-Plus）与 `frontend`（React + antd）

## 背景与问题

用户在对话页测试时反馈 4 个问题：

1. 新建对话后，旧对话从侧栏消失（"被覆盖"）。
2. Agent 提示"当前项目中尚未配置任何源表"，但进入项目时已勾选了表。
3. 流式输出一大段一大段地出现，不流畅。
4. 助手消息 Markdown 不渲染（表格/标题/`---`/`**` 全塌成一行）。

## 设计决策（已与用户确认）

- **选表粒度**：一级。源表归属**对话**，新建对话时从**全部源表**里勾选，不保留项目级表关联。
- **流式**：真·流式（DeepSeek `stream:true` 逐 token 下发），非前端打字机模拟。

## 各项设计

### ① 对话级选表（修复问题 2）

**根因**：勾选的表实际写入 `conversation_tables`（`createConversation` 的 `replaceConversationTables`），但 Agent 的 `list_project_tables` 工具读的是 `project_tables`，且 `project_id` 由 LLM 猜测（日志中它依次试了 1、2…），必然查不到。

**改动**：
- 前端：新建对话改为弹出**选表弹窗**（列出 `listTables("all")`，多选）。确认后 `createConversation(projectId, tableIds)`，表写入 `conversation_tables`。不可不选（至少 1 张）。
- 后端：`AgentDomainService` 的 `list_project_tables` 工具改为读**当前对话**的源表——用 `conversation.getId()` 查 `conversation_tables` → `source_tables`，忽略/移除 LLM 的 `project_id` 入参（工具定义同步去掉该参数及其 required）。
- 前端：移除 `ProjectDetailPage` 的"关联原表"Tab（一级设计下无意义）；保留"对话记录""ETL 作业"两个 Tab。后端 `project_tables` 相关接口暂不删除，仅前端不再使用。

**数据流**：选表弹窗 → `POST /projects/{id}/conversations {table_ids}` → `conversation_tables` → Agent `list_project_tables` 读同表 → 返回真实表清单。

### ② 对话列表覆盖（修复问题 1）

**根因**：后端 `findByProjectId` 正确（返回该项目全部对话、按 `created_at` 倒序）。问题在前端 `onNewConversation` 的乐观 state 拼接。

**改动**：新建对话成功后**重新拉取** `listConversations(projectId)` 刷新侧栏并选中新对话，替代手工 `setConversations([conv, ...prev])`。

### ③ 真·流式输出（修复问题 3）

**根因**：后端非流式调用 LLM，每个 Agent 回合把整段文本作为单个 `token` 事件下发。

**改动**：
- `LlmPort` 新增流式方法：
  ```java
  LlmResponse chatWithToolsStream(messages, tools, systemPrompt, Consumer<String> onTextDelta);
  ```
  提供 **default 实现**：调用现有 `chatWithTools` 并把整段 text 作为一次 delta 回调——保证 `ClaudeAdapter`/`QwenAdapter` 无需改动即可工作（优雅降级）。
- `OpenAiCompatibleAdapter`（DeepSeek/Qwen 共用基类）**重写** `chatWithToolsStream`：请求体加 `"stream": true`，用 OkHttp `response.body().source()` 逐行读 `data: {json}`：
  - `choices[0].delta.content` 片段 → `onTextDelta(片段)` 并累积全文；
  - `choices[0].delta.tool_calls[]`（按 `index` 累积 `id`/`function.name`/`function.arguments` 片段）；
  - 读到 `finish_reason` 决定 `stopReason`；`data: [DONE]` 结束。
  末尾返回与非流式一致的 `LlmResponse`（全文 + 解析好的 toolCalls）。
- `AgentDomainService.run` 改用 `chatWithToolsStream`，`onTextDelta` 回调里 `emit.accept(map("type","token","text",delta))`（token 改为增量片段，不再是整段）。

### ④ Markdown 渲染 + 回合分隔（修复问题 4）

**根因**：`ChatPage.tsx` 把 `{b.content}` 当纯文本渲染，未用已存在的 `MarkdownMessage`（react-markdown + remark-gfm）；且所有回合的 token 累加进同一个气泡，过程叙述与最终答案糊在一起。

**改动**：
- 助手气泡改用 `<MarkdownMessage content={b.content} />`；用户气泡保持纯文本。
- **回合分隔**：后端在每个 Agent 回合的文本结束后（执行工具前 / `end_turn` 时）emit 一个边界事件 `{"type":"turn_end"}`。前端：
  - 收到 `token` 增量：若当前没有"打开中"的助手气泡则新建一个，否则追加；
  - 收到 `turn_end`：关闭当前助手气泡（下一个 `token` 会开新气泡）。
  - 这样 list_project_tables 等不发前端事件的工具回合之间也能正确分隔。

## SSE 事件协议（更新后）

| 事件 | 含义 | 变化 |
|------|------|------|
| `token` | 助手文本**增量片段** | 由"整段"改为"增量" |
| `turn_end` | 一个助手回合结束 | **新增** |
| `schema_proposal` / `steps_proposal` / `step_generated` / `done` / `error` / `stream_end` / `end` | 同前 | 不变 |

前端 SSE 解析器（`data:` 前缀兼容）已修复，无需再改。

## 测试策略

- **后端**：`OpenAiCompatibleAdapter` 流式解析单测（喂入模拟的 `data:` chunk 序列，断言 onTextDelta 增量序列、累积全文、tool_calls 累积、finish_reason→stopReason）；`AgentDomainService` 的 `list_project_tables` 改读 conversation_tables 的单测。
- **前端**：`sse`/ChatPage 对 `token` 增量 + `turn_end` 分多气泡的渲染测试；选表弹窗创建对话、创建后刷新列表的测试；`MarkdownMessage` 已有测试覆盖。

## 影响面与非目标

- 影响：`LlmPort`、`OpenAiCompatibleAdapter`、`AgentDomainService`、`ConversationController`/`AgentAppService`（如需透传 turn_end）、`ChatPage`、`ProjectDetailPage`、新增选表弹窗组件。
- 非目标：不改 Claude/Anthropic 适配器的流式（走 default 降级）；不删后端 `project_tables` 接口；不动鉴权/建表等无关部分。
