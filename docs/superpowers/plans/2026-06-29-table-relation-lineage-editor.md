# 表关系血缘编辑器 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ConversationLineageModal 中增加连线模式，支持点击节点/边来新增、编辑、删除表关系。

**架构**：单文件 UI 改动 `ConversationLineageModal.tsx`。后端关系 CRUD API 已存在（`POST/PUT/DELETE /api/relations`，`POST /api/relations/graph`），前端 API 层已存在（`relations.ts`）。只需在该 Modal 内新增交互状态、连线模式开关、关系编辑弹窗。

**Tech Stack:** React 18, Ant Design 5, G6 v5

---

### 文件结构

- `backend-java/.../interfaces/dto/response/GraphEdgeResponse.java` — 增加 `relationId`
- `backend-java/.../domain/service/RelationDomainService.java` — 构建 graph 时传入 relationId
- `frontend/src/api/types.ts` — `GraphEdge` 增加 `relation_id?`
- `frontend/src/pages/chat/ConversationLineageModal.tsx` — 主要改动文件（~230 行 → ~450 行）

---

### Task 0: 后端 GraphEdge 增加 relationId

**Files:**
- Modify: `backend-java/src/main/java/com/ddh/agent/interfaces/dto/response/GraphEdgeResponse.java`
- Modify: `backend-java/src/main/java/com/ddh/agent/domain/service/RelationDomainService.java`
- Modify: `frontend/src/api/types.ts`

- [ ] **Step 1: GraphEdgeResponse 增加 relationId 字段**

修改 `GraphEdgeResponse.java`：
```java
@Data
public class GraphEdgeResponse {
    private String source;
    private String target;
    private String relationType;
    private List<ColumnPairResponse> columnPairs;
    private Long relationId;  // 新增
}
```

- [ ] **Step 2: RelationDomainService.buildGraph 填充 relationId**

在 `RelationDomainService.java:275-278` 的构建 `GraphEdgeResponse` 处增加：
```java
edge.setRelationId(r.getId());
```
即在 `edge.setRelationType(r.getRelationType());` 之后、`edge.setColumnPairs(...)` 之前插入。

- [ ] **Step 3: 前端 GraphEdge 类型增加 relation_id**

修改 `frontend/src/api/types.ts`：
```typescript
export interface GraphEdge {
  source: string;
  target: string;
  relation_type: string;
  column_pairs: ColumnPair[];
  relation_id?: number;
}
```

- [ ] **Step 4: 运行后端测试确认通过**

```bash
cd backend-java
mvn test 2>&1 | Select-String -Pattern "Tests run"
```
Expected: 16 tests pass.

- [ ] **Step 5: 运行前端测试确认通过**

```bash
cd frontend
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/ddh/agent/interfaces/dto/response/GraphEdgeResponse.java backend-java/src/main/java/com/ddh/agent/domain/service/RelationDomainService.java frontend/src/api/types.ts
git commit -m "feat: add relationId to graph edge response"
```

---

### Task 1: 新增连线模式开关 + 基础状态

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: 新增状态变量和导入**

在文件顶部增加导入：
```tsx
import { Switch } from "antd";
import { CheckCircleOutlined, EditOutlined } from "@ant-design/icons";
```

在 `export function ConversationLineageModal` 内，现有状态之后增加：
```tsx
const [lineMode, setLineMode] = useState(false);
const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

// 缓存节点 ID → 表信息，供字段映射下拉使用
const nodeTableMapRef = useRef<Map<string, { id: number; name: string; columns: GraphColumn[] }>>(new Map());
```

- [ ] **Step 2: 工具栏增加连线模式 Switch**

找到 `关联表管理` 工具栏区域的 JSX，在 `关联表管理` 标题行下方增加：
```tsx
<div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, paddingBottom: 8, borderBottom: "1px solid #f0f0f0" }}>
  <Switch
    size="small"
    checked={lineMode}
    onChange={(v) => {
      setLineMode(v);
      setSelectedNodeId(null);
    }}
  />
  <span style={{ fontSize: 12, color: "#666" }}>连线模式</span>
  {lineMode && <Tag color="orange" style={{ fontSize: 10, lineHeight: "16px", margin: 0 }}>点击两个节点连线</Tag>}
</div>
```

- [ ] **Step 3: 运行测试确认编译通过**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试全部通过。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: add line mode switch to lineage modal"
```

---

### Task 2: 定义 editRelation 状态 + getRelationId + nodeTableMap 逻辑

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: 定义 EditRelationState 类型和状态**

在 `export function ConversationLineageModal` 内，其他状态之后增加：
```tsx
interface EditRelationState {
  sourceTable: { id: number; name: string; columns: GraphColumn[] };
  targetTable: { id: number; name: string; columns: GraphColumn[] };
  sourceTableId: number;
  targetTableId: number;
  relationType: string;
  description: string;
  columnPairs: { sourceColumnId: number | undefined; targetColumnId: number | undefined }[];
  existingRelationId?: number;
}

const [editRelation, setEditRelation] = useState<EditRelationState | null>(null);

const getRelationId = (edge: GraphEdge): number | undefined => edge.relation_id;
```

- [ ] **Step 2: 渲染图时缓存 nodeTableMap**

在 `data = await lineageGraph(selectedIds)` 之后增加：
```tsx
const map = new Map<string, { id: number; name: string; columns: GraphColumn[] }>();
data.nodes.forEach((n) => {
  const tableId = Number(n.id);
  const t = allTables.find((x) => x.id === tableId);
  map.set(n.id, { id: tableId, name: t?.name ?? n.table_name, columns: n.columns });
});
nodeTableMapRef.current = map;
```

- [ ] **Step 3: 增加 lineMode ref**

```tsx
const lineModeRef = useRef(false);
lineModeRef.current = lineMode;
```

- [ ] **Step 4: 运行测试**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: edit relation state and graph data caching"
```

---

### Task 3: 连线模式节点选中交互 + 边点击事件

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: 在 graph render 后增加节点/边点击监听**

在 `useEffect([open, selectedIds])` 的 render 函数内，`await graph.render()` 之后增加：

```tsx
graph.on("node:click", (event: any) => {
  if (!lineModeRef.current) return;
  const clickedId = event.target?.id;
  if (!clickedId) return;

  setSelectedNodeId((prev) => {
    if (prev === null) return clickedId;
    if (prev === clickedId) return null;

    const sourceId = prev;
    const targetId = clickedId;
    const sourceTable = nodeTableMapRef.current.get(sourceId);
    const targetTable = nodeTableMapRef.current.get(targetId);
    if (sourceTable && targetTable) {
      const existingEdge = dataRef.current?.edges.find(
        (e) => e.source === sourceId && e.target === targetId
      );
      setEditRelation({
        sourceTable,
        targetTable,
        sourceTableId: sourceTable.id,
        targetTableId: targetTable.id,
        relationType: existingEdge?.relation_type ?? "ONE_TO_ONE",
        description: "",
        columnPairs: existingEdge?.column_pairs?.map((cp) => ({
          sourceColumnId: cp.source_column_id,
          targetColumnId: cp.target_column_id,
        })) ?? [],
        existingRelationId: existingEdge ? getRelationId(existingEdge) : undefined,
      });
    }
    return null;
  });
});

graph.on("edge:click", (event: any) => {
  if (lineModeRef.current) return;
  const edgeId = event.target?.id;
  if (!edgeId) return;
  const edgeData = graph.getEdgeData(edgeId);
  if (!edgeData) return;
  const source = edgeData.source as string;
  const target = edgeData.target as string;
  const sourceTable = nodeTableMapRef.current.get(source);
  const targetTable = nodeTableMapRef.current.get(target);
  const edgeInfo = dataRef.current?.edges.find(
    (e) => e.source === source && e.target === target
  );
  if (!sourceTable || !targetTable || !edgeInfo) return;

  setEditRelation({
    sourceTable,
    targetTable,
    sourceTableId: sourceTable.id,
    targetTableId: targetTable.id,
    relationType: edgeInfo.relation_type,
    description: "",
    columnPairs: edgeInfo.column_pairs?.map((cp) => ({
      sourceColumnId: cp.source_column_id,
      targetColumnId: cp.target_column_id,
    })) ?? [],
    existingRelationId: getRelationId(edgeInfo),
  });
});
```

> 注意：G6 v5 事件 API 可能使用 `event.target.id` 或不同路径获取节点/边 ID，实现时需要根据实际 G6 版本调试。

在 `render()` 的 cleanup 中移除监听：
```tsx
graph.off("node:click");
graph.off("edge:click");
```

- [ ] **Step 2: 运行测试**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: node/edge click interaction for line mode"
```

---

### Task 4: 关系编辑弹窗组件

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: 在 return JSX 中增加关系编辑 Modal**

在 `</Modal>` 闭合标签之前（即在 ConversationLineageModal 的 Modal 内），增加：

```tsx
<Modal
  title={`${editRelation.sourceTable.name} ←→ ${editRelation.targetTable.name}`}
  open={editRelation !== null}
  onCancel={() => setEditRelation(null)}
  width={520}
  footer={null}
  destroyOnClose
>
  {/* 关系类型 */}
  <div style={{ marginBottom: 16 }}>
    <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>关系类型</div>
    <Radio.Group
      value={editRelation?.relationType}
      onChange={(e) => setEditRelation((prev) => prev ? { ...prev, relationType: e.target.value } : null)}
    >
      <Radio value="ONE_TO_ONE">一对一</Radio>
      <Radio value="ONE_TO_MANY">一对多</Radio>
      <Radio value="MANY_TO_ONE">多对一</Radio>
      <Radio value="MANY_TO_MANY">多对多</Radio>
    </Radio.Group>
  </div>

  {/* 字段映射 */}
  <div style={{ marginBottom: 16 }}>
    <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>
      字段映射
      <Button type="link" size="small" style={{ padding: 0, marginLeft: 8 }}
        onClick={() => setEditRelation((prev) => prev ? {
          ...prev,
          columnPairs: [...prev.columnPairs, { sourceColumnId: undefined, targetColumnId: undefined }],
        } : null)}
      >
        + 添加
      </Button>
    </div>
    {editRelation?.columnPairs.map((pair, i) => (
      <div key={i} style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
        <Select
          style={{ flex: 1 }}
          placeholder="源字段"
          value={pair.sourceColumnId}
          onChange={(val) => {
            const newPairs = [...editRelation.columnPairs];
            newPairs[i] = { ...newPairs[i], sourceColumnId: val };
            setEditRelation({ ...editRelation, columnPairs: newPairs });
          }}
          options={editRelation.sourceTable.columns.map((c) => ({
            label: `${c.name} (${c.type})`,
            value: c.id,
          }))}
          showSearch
          filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
        />
        <span style={{ color: "#999" }}>→</span>
        <Select
          style={{ flex: 1 }}
          placeholder="目标字段"
          value={pair.targetColumnId}
          onChange={(val) => {
            const newPairs = [...editRelation.columnPairs];
            newPairs[i] = { ...newPairs[i], targetColumnId: val };
            setEditRelation({ ...editRelation, columnPairs: newPairs });
          }}
          options={editRelation.targetTable.columns.map((c) => ({
            label: `${c.name} (${c.type})`,
            value: c.id,
          }))}
          showSearch
          filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
        />
        <Button
          type="text"
          size="small"
          danger
          icon={<CloseOutlined />}
          onClick={() => {
            const newPairs = editRelation.columnPairs.filter((_, j) => j !== i);
            setEditRelation({ ...editRelation, columnPairs: newPairs });
          }}
        />
      </div>
    ))}
  </div>

  {/* 描述 */}
  <div style={{ marginBottom: 16 }}>
    <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>描述（可选）</div>
    <Input.TextArea
      rows={2}
      value={editRelation?.description ?? ""}
      onChange={(e) => setEditRelation((prev) => prev ? { ...prev, description: e.target.value } : null)}
      placeholder="描述该关系的业务含义..."
    />
  </div>

  {/* 按钮 */}
  <div style={{ display: "flex", justifyContent: "space-between" }}>
    <div>
      {editRelation?.existingRelationId != null && (
        <Button danger onClick={handleDeleteRelation}>删除关系</Button>
      )}
    </div>
    <Space>
      <Button onClick={() => setEditRelation(null)}>取消</Button>
      <Button type="primary" onClick={handleSaveRelation}>保存</Button>
    </Space>
  </div>
</Modal>
```

- [ ] **Step 3: 运行测试确认编译通过**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试通过。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: relation edit modal with type selection and column mapping"
```

---

### Task 5: 保存/删除关系 API 调用 + 图刷新

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: 增加导入**

在文件顶部导入已有的关系 API：
```tsx
import { createRelation, updateRelation, deleteRelation } from "../../api/relations";
```

- [ ] **Step 2: 实现 handleSaveRelation**

在 `handleExportJson` 之后、`return` 之前增加：
```tsx
const handleSaveRelation = async () => {
  if (!editRelation) return;
  const data: import("../../api/types").RelationSave = {
    source_table_id: editRelation.sourceTableId,
    target_table_id: editRelation.targetTableId,
    relation_type: editRelation.relationType,
    description: editRelation.description || undefined,
    column_pairs: editRelation.columnPairs
      .filter((p) => p.sourceColumnId != null && p.targetColumnId != null)
      .map((p) => ({ source_column_id: p.sourceColumnId!, target_column_id: p.targetColumnId! })),
  };
  try {
    if (editRelation.existingRelationId != null) {
      await updateRelation(editRelation.existingRelationId, data);
    } else {
      await createRelation(data);
    }
    message.success("关系保存成功");
    setEditRelation(null);
    // 触发图刷新——重新执行 useEffect([open, selectedIds]) 的逻辑
    // 通过 key 或直接调用 refreshGraph
    refreshGraph();
  } catch {
    message.error("保存关系失败");
  }
};

const handleDeleteRelation = async () => {
  if (!editRelation?.existingRelationId) return;
  try {
    await deleteRelation(editRelation.existingRelationId);
    message.success("关系已删除");
    setEditRelation(null);
    refreshGraph();
  } catch {
    message.error("删除关系失败");
  }
};
```

- [ ] **Step 3: 实现 refreshGraph**

将现有 `useEffect([open, selectedIds])` 中的渲染逻辑抽取为 `renderGraph` 函数，然后增加：
```tsx
const [graphKey, setGraphKey] = useState(0);

const refreshGraph = () => setGraphKey((k) => k + 1);
```

将 `useEffect([open, selectedIds])` 改为 `useEffect([open, selectedIds, graphKey])`，确保刷新时重新渲染图。

- [ ] **Step 4: 连线模式下禁用表列表操作**

连线模式开启时，将 Select 和添加按钮置为 disabled：
在 Select 上增加 `disabled={lineMode}`，在添加按钮上增加 `disabled={lineMode || addValue == null}`。

- [ ] **Step 5: 运行测试**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试通过。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: save/delete relations with graph refresh"
```

---

### Task 6: 节点选中高亮视觉反馈

**Files:**
- Modify: `frontend/src/pages/chat/ConversationLineageModal.tsx`

- [ ] **Step 1: selectedNodeId 变化时高亮节点**

在 `selectedNodeId` state 之上增加一个 `useEffect`：
```tsx
useEffect(() => {
  const graph = graphRef.current;
  if (!graph || !graph.destroyed) return;
  const nodes = [...nodeTableMapRef.current.keys()];
  // 重置所有节点样式
  nodes.forEach((id) => {
    graph.updateNodeData([{ id, style: { stroke: "#1677ff", lineWidth: 1.5 } }]);
  });
  // 高亮选中节点
  if (selectedNodeId && nodes.includes(selectedNodeId)) {
    graph.updateNodeData([{ id: selectedNodeId, style: { stroke: "#ff7a00", lineWidth: 3 } }]);
  }
  graph.draw();
}, [selectedNodeId]);
```

- [ ] **Step 2: 运行测试**

```bash
npm test 2>&1 | Select-String -Pattern "Tests|pass|fail"
```
Expected: 26 测试通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/chat/ConversationLineageModal.tsx
git commit -m "feat: selected node visual highlight in line mode"
```
