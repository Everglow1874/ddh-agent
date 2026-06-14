# 对话页四项问题修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复对话页的选表失效、对话列表覆盖、流式不流畅、Markdown 不渲染四个问题。

**Architecture:** 源表归属对话（conversation_tables），Agent 工具改读对话表；LLM 走真·流式（DeepSeek `stream:true`）逐 token 下发，每个回合一个独立气泡；前端用 react-markdown 渲染助手消息。

**Tech Stack:** Spring Boot 2.7 + MyBatis-Plus + OkHttp（后端）；React + antd + react-markdown（前端）。

**说明：** 按用户偏好，任务粒度放粗（按功能聚合，不做逐条 TDD 微拆）。每个任务含确切文件、完整代码、验证命令、提交。设计文档见 `docs/superpowers/specs/2026-06-14-chat-ux-fixes-design.md`。

---

## File Structure

- `backend-java/.../domain/service/LlmPort.java` — 接口加流式默认方法。
- `backend-java/.../infrastructure/llm/OpenAiCompatibleAdapter.java` — 真流式实现 + 可测的解析方法。
- `backend-java/.../infrastructure/llm/DynamicLlmPort.java` — 透传流式调用到委托。
- `backend-java/.../domain/service/AgentDomainService.java` — 工具改读对话表；run() 用流式 + 发 `turn_end`。
- `backend-java/.../infrastructure/llm/OpenAiStreamParseTest.java`（新）— 流式解析单测。
- `frontend/src/api/types.ts` — SSEEvent 加 `turn_end`。
- `frontend/src/api/sse.ts` — 无需改（已兼容 `data:`）。
- `frontend/src/pages/ChatPage.tsx` — 流式分气泡 + Markdown + 新建对话弹窗。
- `frontend/src/pages/chat/NewConversationModal.tsx`（新）— 选表弹窗。
- `frontend/src/pages/chat/NewConversationModal.test.tsx`（新）— 弹窗测试。
- `frontend/src/pages/ProjectDetailPage.tsx` — 移除"关联原表"Tab。

---

## Task 1: 后端 Agent 工具改读对话源表（修复问题 2 后端）

**Files:**
- Modify: `backend-java/src/main/java/com/ddh/agent/domain/service/AgentDomainService.java`

- [ ] **Step 1: 改 `list_project_tables` 工具实现** — 读当前对话的 `conversation_tables`，不再用 LLM 传入的 project_id。

替换 `executeTool` 里的 `case "list_project_tables"` 整块为：

```java
case "list_project_tables": {
    List<ConversationTable> rows =
        conversationRepository.findTablesByConversationId(conv.getId());
    List<Map<String, Object>> tables = rows.stream().map(ct -> {
        Optional<SourceTable> t = sourceTableRepository.findById(ct.getTableId());
        if (!t.isPresent()) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.get().getId());
        m.put("name", t.get().getName());
        m.put("scope", t.get().getScope());
        return m;
    }).filter(Objects::nonNull).collect(Collectors.toList());
    return map("tables", tables);
}
```

（`ConversationTable`、`conversationRepository`、`sourceTableRepository` 均已在类中可用。`projectRepository` 仍被其它工具使用，保留。）

- [ ] **Step 2: 工具定义去掉 project_id 入参** — 在 `ALL_TOOLS` 里把 `list_project_tables` 那行改为无参，并新增 `noParams()` 辅助方法。

把：
```java
tool("list_project_tables", "Get all source tables selected for this ETL project.",
    paramsSingle("project_id", "integer", "The project ID")),
```
改为：
```java
tool("list_project_tables", "Get all source tables selected for this conversation.",
    noParams()),
```
在"工具定义构建器"区新增：
```java
private static Map<String, Object> noParams() {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("type", "object");
    p.put("properties", new LinkedHashMap<>());
    return p;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd backend-java && mvn -q compile`
Expected: BUILD SUCCESS（无编译错误）。

- [ ] **Step 4: 提交**

```bash
git add backend-java/src/main/java/com/ddh/agent/domain/service/AgentDomainService.java
git commit -m "fix(java): list_project_tables 改读当前对话的源表(conversation_tables)"
```

---

## Task 2: 后端真·流式（修复问题 3）

**Files:**
- Modify: `backend-java/src/main/java/com/ddh/agent/domain/service/LlmPort.java`
- Modify: `backend-java/src/main/java/com/ddh/agent/infrastructure/llm/OpenAiCompatibleAdapter.java`
- Modify: `backend-java/src/main/java/com/ddh/agent/infrastructure/llm/DynamicLlmPort.java`
- Modify: `backend-java/src/main/java/com/ddh/agent/domain/service/AgentDomainService.java`
- Test: `backend-java/src/test/java/com/ddh/agent/infrastructure/llm/OpenAiStreamParseTest.java`

- [ ] **Step 1: `LlmPort` 加流式默认方法** — 默认实现降级为整段一次回调（Claude/Qwen 等不改也能用）。

在 `LlmPort` 接口里 `chatWithTools(...)` 下方加：
```java
default LlmResponse chatWithToolsStream(List<Map<String, Object>> messages,
                                        List<Map<String, Object>> tools,
                                        String systemPrompt,
                                        java.util.function.Consumer<String> onTextDelta) {
    LlmResponse r = chatWithTools(messages, tools, systemPrompt);
    if (r.text != null && !r.text.isEmpty()) onTextDelta.accept(r.text);
    return r;
}
```

- [ ] **Step 2: `DynamicLlmPort` 透传流式** — 否则默认方法会走非流式委托，丢掉真流式。

在 `DynamicLlmPort` 加：
```java
@Override
public LlmResponse chatWithToolsStream(List<Map<String, Object>> messages,
                                       List<Map<String, Object>> tools,
                                       String systemPrompt,
                                       java.util.function.Consumer<String> onTextDelta) {
    return delegate.get().chatWithToolsStream(messages, tools, systemPrompt, onTextDelta);
}
```

- [ ] **Step 3: `OpenAiCompatibleAdapter` 抽出请求体构建 + 实现流式**

把 `chatWithTools` 里构建 `body` 的部分抽成方法（供流式复用）。在类中加：
```java
protected ObjectNode buildRequestBody(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      String systemPrompt) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    ArrayNode msgs = body.putArray("messages");
    if (systemPrompt != null && !systemPrompt.isEmpty()) {
        ObjectNode sys = msgs.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
    }
    for (Map<String, Object> m : messages) {
        msgs.add(toOpenAiMessage(m));
    }
    if (tools != null && !tools.isEmpty()) {
        ArrayNode toolsNode = body.putArray("tools");
        for (Map<String, Object> t : tools) {
            ObjectNode tool = toolsNode.addObject();
            tool.put("type", "function");
            ObjectNode fn = tool.putObject("function");
            fn.put("name", (String) t.get("name"));
            fn.put("description", (String) t.get("description"));
            fn.set("parameters", mapper.valueToTree(t.get("parameters")));
        }
    }
    return body;
}
```
并把 `chatWithTools` 中那段构建 `body` 的代码替换为：`ObjectNode body = buildRequestBody(messages, tools, systemPrompt);`（其余不变）。

在类中加流式方法与可测的解析方法、以及 tool_call 累积器：
```java
@Override
public LlmResponse chatWithToolsStream(List<Map<String, Object>> messages,
                                       List<Map<String, Object>> tools,
                                       String systemPrompt,
                                       java.util.function.Consumer<String> onTextDelta) {
    try {
        ObjectNode body = buildRequestBody(messages, tools, systemPrompt);
        body.put("stream", true);
        Request request = new Request.Builder()
            .url(apiUrl)
            .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String rb = response.body() != null ? response.body().string() : "";
                throw new RuntimeException(apiUrl + " error " + response.code() + ": " + rb);
            }
            okio.BufferedSource source = response.body().source();
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                lines.add(line);
            }
            return consumeStream(lines, onTextDelta);
        }
    } catch (IOException e) {
        throw new RuntimeException("LLM streaming call failed: " + apiUrl, e);
    }
}

/** 解析 OpenAI 兼容流式 data: 行序列（包级可见，便于单测）。 */
LlmResponse consumeStream(List<String> lines, java.util.function.Consumer<String> onTextDelta) {
    StringBuilder fullText = new StringBuilder();
    java.util.Map<Integer, ToolCallBuilder> toolBuilders = new java.util.LinkedHashMap<>();
    String finishReason = "stop";
    for (String raw : lines) {
        if (raw == null || !raw.startsWith("data:")) continue;
        String data = raw.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) continue;
        JsonNode root;
        try { root = mapper.readTree(data); } catch (Exception e) { continue; }
        JsonNode choice = root.path("choices").path(0);
        JsonNode delta = choice.path("delta");
        JsonNode contentNode = delta.path("content");
        if (contentNode.isTextual()) {
            String piece = contentNode.asText();
            if (!piece.isEmpty()) {
                fullText.append(piece);
                onTextDelta.accept(piece);
            }
        }
        for (JsonNode tc : delta.path("tool_calls")) {
            int idx = tc.path("index").asInt(0);
            ToolCallBuilder b = toolBuilders.computeIfAbsent(idx, k -> new ToolCallBuilder());
            if (tc.hasNonNull("id")) b.id = tc.path("id").asText();
            JsonNode fn = tc.path("function");
            if (fn.hasNonNull("name")) b.name = fn.path("name").asText();
            if (fn.hasNonNull("arguments")) b.args.append(fn.path("arguments").asText());
        }
        String fr = choice.path("finish_reason").asText("");
        if (!fr.isEmpty() && !"null".equals(fr)) finishReason = fr;
    }
    String stopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";
    List<ToolCall> toolCalls = new ArrayList<>();
    for (ToolCallBuilder b : toolBuilders.values()) {
        Map<String, Object> input;
        try {
            input = b.args.length() > 0
                ? mapper.readValue(b.args.toString(), Map.class) : new HashMap<>();
        } catch (Exception e) { input = new HashMap<>(); }
        toolCalls.add(new ToolCall(b.id, b.name, input));
    }
    String text = fullText.length() > 0 ? fullText.toString() : null;
    return new LlmResponse(text, stopReason, toolCalls);
}

private static class ToolCallBuilder {
    String id; String name; StringBuilder args = new StringBuilder();
}
```
（`LlmResponse`/`ToolCall` 来自 `LlmPort`，已 import；新增用到 `okio.BufferedSource`，OkHttp 自带 okio，无需加依赖。）

- [ ] **Step 4: 写流式解析单测**

Create `backend-java/src/test/java/com/ddh/agent/infrastructure/llm/OpenAiStreamParseTest.java`：
```java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort.LlmResponse;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamParseTest {

    private DeepSeekAdapter adapter() {
        return new DeepSeekAdapter(new OkHttpClient(), "k", "deepseek-chat");
    }

    @Test
    void accumulatesContentDeltasAndEmitsEach() {
        List<String> lines = Arrays.asList(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}",
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "data: [DONE]");
        List<String> deltas = new ArrayList<>();
        LlmResponse r = adapter().consumeStream(lines, deltas::add);
        assertEquals(Arrays.asList("你好", "，世界"), deltas);
        assertEquals("你好，世界", r.text);
        assertEquals("end_turn", r.stopReason);
        assertTrue(r.toolCalls.isEmpty());
    }

    @Test
    void accumulatesToolCallArgumentFragments() {
        List<String> lines = Arrays.asList(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"get_table_schema\",\"arguments\":\"{\\\"table\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"_id\\\":5}\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
            "data: [DONE]");
        LlmResponse r = adapter().consumeStream(lines, s -> {});
        assertEquals("tool_use", r.stopReason);
        assertEquals(1, r.toolCalls.size());
        assertEquals("get_table_schema", r.toolCalls.get(0).name);
        assertEquals(5, ((Number) r.toolCalls.get(0).input.get("table_id")).intValue());
    }
}
```

- [ ] **Step 5: `AgentDomainService.run` 改用流式 + 发 turn_end**

在 `run()` 的 `while (true)` 循环里，把：
```java
LlmPort.LlmResponse response = llmPort.chatWithTools(messages, tools, system);

if (response.text != null && !response.text.isEmpty()) {
    emit.accept(map("type", "token", "text", response.text));
}
```
替换为：
```java
LlmPort.LlmResponse response = llmPort.chatWithToolsStream(
    messages, tools, system,
    delta -> emit.accept(map("type", "token", "text", delta)));

emit.accept(map("type", "turn_end"));
```
（其余循环逻辑不变：end_turn 保存助手消息并 break；否则处理工具调用。）

- [ ] **Step 6: 跑后端测试**

Run: `cd backend-java && mvn -q test`
Expected: BUILD SUCCESS，新增 2 个流式解析测试通过，原有测试不回归。

- [ ] **Step 7: 提交**

```bash
git add backend-java/src/main/java/com/ddh/agent/domain/service/LlmPort.java \
        backend-java/src/main/java/com/ddh/agent/infrastructure/llm/OpenAiCompatibleAdapter.java \
        backend-java/src/main/java/com/ddh/agent/infrastructure/llm/DynamicLlmPort.java \
        backend-java/src/main/java/com/ddh/agent/domain/service/AgentDomainService.java \
        backend-java/src/test/java/com/ddh/agent/infrastructure/llm/OpenAiStreamParseTest.java
git commit -m "feat(java): LLM 真·流式输出，逐 token 下发并按回合发 turn_end"
```

---

## Task 3: 前端流式分气泡 + Markdown 渲染（修复问题 3/4 前端）

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: SSEEvent 加 `turn_end`**

在 `frontend/src/api/types.ts` 的 `SSEEvent` 联合类型里加一行：
```ts
  | { type: "turn_end" }
```
（加在 `| { type: "stream_end" }` 之前。）

- [ ] **Step 2: ChatPage 引入 MarkdownMessage + 改 runStream 分气泡**

在 `ChatPage.tsx` 顶部 import 区加：
```tsx
import { MarkdownMessage } from "./chat/MarkdownMessage";
```
把 `runStream` 整个函数替换为（去掉 `assistantText` 累加，改为按 turn_end 分气泡、token 增量追加）：
```tsx
  const runStream = (cid: number) => {
    setStreaming(true);
    let bubbleOpen = false;
    abortRef.current = streamConversation(
      cid,
      (event: SSEEvent) => {
        switch (event.type) {
          case "token":
            setBubbles((prev) => {
              const next = [...prev];
              if (bubbleOpen && next.length && next[next.length - 1].role === "assistant") {
                next[next.length - 1] = {
                  role: "assistant",
                  content: next[next.length - 1].content + event.text,
                };
              } else {
                next.push({ role: "assistant", content: event.text });
              }
              return next;
            });
            bubbleOpen = true;
            break;
          case "turn_end":
            bubbleOpen = false;
            break;
          case "schema_proposal":
            setSchemaProposal({ target_table: event.target_table, columns: event.columns });
            break;
          case "steps_proposal":
            setStepsProposal(event.steps);
            break;
          case "step_generated":
            setGeneratedSteps((prev) => [
              ...prev,
              { step_order: event.step_order, step_name: event.step_name, sql: event.sql },
            ]);
            break;
          case "done":
            setJobId(event.job_id);
            antdMessage.success("ETL 作业已生成");
            break;
          case "error":
            antdMessage.error(event.message);
            break;
          case "stream_end":
          case "end":
            setStreaming(false);
            break;
        }
      },
      (err) => {
        antdMessage.error(err.message);
        setStreaming(false);
      }
    );
  };
```

- [ ] **Step 3: 助手气泡用 Markdown 渲染**

在 JSX 的气泡渲染里，把：
```tsx
                {b.content}
```
替换为：
```tsx
                {b.role === "assistant" ? <MarkdownMessage content={b.content} /> : b.content}
```

- [ ] **Step 4: 前端类型检查 + 已有测试**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 类型无错；现有测试全绿（ChatPage 旧的整段 token 仍兼容：单 token 即单气泡）。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/api/types.ts frontend/src/pages/ChatPage.tsx
git commit -m "feat(frontend): 流式按回合分气泡 + 助手消息 Markdown 渲染"
```

---

## Task 4: 前端新建对话选表弹窗 + 列表刷新（修复问题 1/2 前端）

**Files:**
- Create: `frontend/src/pages/chat/NewConversationModal.tsx`
- Create: `frontend/src/pages/chat/NewConversationModal.test.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: 新建选表弹窗组件**

Create `frontend/src/pages/chat/NewConversationModal.tsx`：
```tsx
import { useEffect, useState } from "react";
import { Modal, Select } from "antd";
import { listTables } from "../../api/tables";
import type { SourceTable } from "../../api/types";

interface Props {
  open: boolean;
  onCancel: () => void;
  onConfirm: (tableIds: number[]) => void;
}

export function NewConversationModal({ open, onCancel, onConfirm }: Props) {
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [selected, setSelected] = useState<number[]>([]);

  useEffect(() => {
    if (open) {
      setSelected([]);
      listTables("all").then(setTables);
    }
  }, [open]);

  return (
    <Modal
      title="新建对话 · 选择源表"
      open={open}
      onCancel={onCancel}
      onOk={() => onConfirm(selected)}
      okText="创建"
      okButtonProps={{ disabled: selected.length === 0 }}
    >
      <Select
        mode="multiple"
        style={{ width: "100%" }}
        placeholder="选择本次对话要用到的源表（可多选）"
        value={selected}
        onChange={setSelected}
        options={tables.map((t) => ({ label: t.name, value: t.id }))}
      />
    </Modal>
  );
}
```

- [ ] **Step 2: 弹窗测试**

Create `frontend/src/pages/chat/NewConversationModal.test.tsx`：
```tsx
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { NewConversationModal } from "./NewConversationModal";

vi.mock("../../api/tables", () => ({
  listTables: vi.fn().mockResolvedValue([
    { id: 1, name: "成绩表", description: null, scope: 2, owner_id: 1, created_at: "" },
    { id: 2, name: "班级表", description: null, scope: 2, owner_id: 1, created_at: "" },
  ]),
}));

describe("NewConversationModal", () => {
  beforeEach(() => vi.clearAllMocks());

  it("打开时加载源表列表", async () => {
    render(<NewConversationModal open onCancel={() => {}} onConfirm={() => {}} />);
    await waitFor(() => expect(screen.getByText("新建对话 · 选择源表")).toBeInTheDocument());
  });

  it("未选表时创建按钮禁用", async () => {
    render(<NewConversationModal open onCancel={() => {}} onConfirm={() => {}} />);
    const okBtn = await screen.findByRole("button", { name: "创建" });
    expect(okBtn).toBeDisabled();
  });
});
```

- [ ] **Step 3: ChatPage 接入弹窗 + 创建后重新拉取列表**

在 `ChatPage.tsx`：
1. import：`import { NewConversationModal } from "./chat/NewConversationModal";`
2. 加状态：`const [newModalOpen, setNewModalOpen] = useState(false);`
3. 把 `onNewConversation` 替换为：
```tsx
  const onNewConversation = () => setNewModalOpen(true);

  const handleCreateConversation = async (tableIds: number[]) => {
    setNewModalOpen(false);
    const conv = await createConversation(projectId, tableIds);
    const convs = await listConversations(projectId); // 重新拉取，修复"旧对话被覆盖"
    setConversations(convs);
    selectConversation(conv.id);
  };
```
4. 在 `<Content>...</Content>` 之后、`<Sider width={320}...>` 之前插入弹窗：
```tsx
      <NewConversationModal
        open={newModalOpen}
        onCancel={() => setNewModalOpen(false)}
        onConfirm={handleCreateConversation}
      />
```

- [ ] **Step 4: 测试**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 类型无错；新增 NewConversationModal 测试通过，其余不回归。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/pages/chat/NewConversationModal.tsx \
        frontend/src/pages/chat/NewConversationModal.test.tsx \
        frontend/src/pages/ChatPage.tsx
git commit -m "feat(frontend): 新建对话选表弹窗 + 创建后刷新对话列表"
```

---

## Task 5: 移除项目级"关联原表"Tab（一级选表收尾）

**Files:**
- Modify: `frontend/src/pages/ProjectDetailPage.tsx`

- [ ] **Step 1: 删掉 tables Tab 及其相关代码**

在 `ProjectDetailPage.tsx`：
1. 删除 `Tabs` 的 `items` 数组里 `key: "tables"` 那个对象（"关联原表" Tab）。
2. 删除不再使用的内容：`onTransferChange` 函数、`targetKeys` state、`allTables` state 及其 `setAllTables(await listTables("all"))` 调用。
3. 删除随之不再使用的 import：`Transfer`、`TransferProps`、`listTables`、`associateTables`、`removeTableFromProject`、`SourceTable`。保留 `Tabs/Button/Table/message/Space/Card` 中仍被用到的。

- [ ] **Step 2: 类型检查（确认无未使用/缺失引用）**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 无 TS 错误（含 noUnusedLocals 若启用）；测试全绿。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/pages/ProjectDetailPage.tsx
git commit -m "refactor(frontend): 移除项目级关联原表Tab(改为对话级选表)"
```

---

## Task 6: 端到端手动验证

- [ ] **Step 1: 启动并走查**

启动后端（`backend-java`）与前端（`frontend`）。在 DeepSeek `api-key` 已配置的前提下：
1. 进入某项目 → 点"进入 Agent 对话" → 点"+新对话" → 弹窗勾选成绩表/班级表/学生表 → 创建。
2. 发消息"查询今年每次成绩各个班级的平均分"。
3. 预期：
   - **流式**：助手文字逐字平滑出现，不再一大段蹦（问题 3 ✓）。
   - **Markdown**：表格/标题/`---`/`**` 正常渲染（问题 4 ✓）。
   - **分气泡**：过程叙述与最终答案是分开的气泡（问题 4 ✓）。
   - **有表可用**：Agent 不再说"尚未配置任何源表"，能列出所选表（问题 2 ✓）。
   - 再点"+新对话"创建第二个对话后，侧栏两条对话都在，旧的不消失（问题 1 ✓）。

- [ ] **Step 2: 若全部通过，无需额外提交**（前面任务已分别提交）。

---

## Self-Review

- **Spec 覆盖**：① 对话级选表 → Task 1（后端）+ Task 4（前端弹窗）+ Task 5（移除项目级 Tab）；② 列表覆盖 → Task 4 Step 3 重新拉取；③ 真流式 → Task 2；④ Markdown+回合分隔 → Task 3。全部覆盖。
- **占位符**：无 TBD/TODO，关键代码均给全。
- **类型一致**：`chatWithToolsStream(messages, tools, systemPrompt, Consumer<String>)` 在 LlmPort/DynamicLlmPort/OpenAiCompatibleAdapter/AgentDomainService 四处签名一致；`consumeStream(List<String>, Consumer<String>)` 与测试调用一致；SSE `turn_end` 在后端 emit 与前端 types/handler 一致；`createConversation(projectId, tableIds)` 与既有 api 签名一致。
