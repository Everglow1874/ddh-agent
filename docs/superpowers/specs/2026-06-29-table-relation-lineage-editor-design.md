# 表关系血缘编辑器 — 设计文档

日期：2026-06-29
范围：`frontend/src/pages/chat/ConversationLineageModal.tsx` 及关系 API（已有）

## 背景

现有功能：用户在对话侧栏底部可打开血缘图 Modal，查看已选表之间的关系图。图由 G6 dagre 渲染，可拖拽、缩放、悬停查看字段。

用户需求：直接在血缘图上**维护表之间的关联关系**（新增、编辑、删除关系，定义关系类型和字段映射），无需切换页面。

## 现状

后端与前端 API 已完整：

- `POST /api/relations` — 创建关系
- `PUT /api/relations/{id}` — 编辑关系
- `DELETE /api/relations/{id}` — 删除关系
- `POST /api/relations/graph` — 获取血缘图数据
- 前端 `relations.ts` — `createRelation` / `updateRelation` / `deleteRelation` / `lineageGraph`
- `RelationSave` 类型：`{ source_table_id, target_table_id, relation_type, description?, column_pairs: [{source_column_id, target_column_id}] }`

现有 Modal 的工具栏已有"保存"按钮（保存对话表列表），保留不动。

## 设计方案（已与用户确认）

### 交互流程

**连线模式开关**：工具栏增加 Switch，标签"连线模式"，默认关闭。

**关闭连线模式（默认）**：
- 拖动画布、缩放、点击节点无选中操作
- 点击已有边 → 弹出关系编辑弹窗（查看/修改/删除）

**开启连线模式**：
- 禁用 `drag-canvas` behavior 避免误触，保留 `zoom-canvas` 和 `drag-element`
- 点击节点 A → 高亮选中（蓝色边框加粗 + 发光效果）
- 点击节点 B → 若 A ≠ B，弹出关系编辑弹窗，预填 `source_table_id = A.table_id`、`target_table_id = B.table_id`
- 若 A = B（点了同一个节点），取消选中
- 弹窗关闭后，清除选中态，可继续点下一对
- 若 A 和 B 之间已有关系，弹窗预填现有数据（不重复创建）

**关系编辑弹窗**：
- 标题：`成绩表 ←→ 班级表`
- 关系类型：Radio 组 — 一对一 / 一对多 / 多对一 / 多对多
- 字段映射：可增删的多行，每行两个 Select（源字段 / 目标字段）
- 描述：可选 TextArea
- 底部按钮：保存 / 删除（仅已有关系统显示） / 取消
- 字段下拉数据从富图数据的 `node.columns` 获取（无需额外请求）

**保存后**：调用 `createRelation` 或 `updateRelation`，成功后刷新图（重新调用 `lineageGraph(selectedIds)`）。

**删除时**：调用 `deleteRelation`，成功后刷新图。

### 前端改动文件

#### `ConversationLineageModal.tsx`

新增状态：
- `lineMode: boolean` — 连线模式开关
- `selectedNodeId: string | null` — 连线模式选中的第一个节点 ID
- `editRelation: { sourceTableId: number; targetTableId: number; existingId?: number } | null` — 编辑弹窗数据
- `nodeTableMap: Map<string, { id: number; name: string; columns: GraphColumn[] }>` — 节点 ID → 表信息，用于弹窗字段下拉

工具栏变化：
- 在"关联表管理"下方新增 Switch：`连线模式 {lineMode}`
- 连线模式下，Select 和添加按钮置灰或隐藏（避免同时编辑）

G6 图交互变化：
- `behaviors` 根据 `lineMode` 动态调整：
  - lineMode = true → `drag-canvas` 移除，改成 `click` 事件监听节点点击
  - lineMode = false → 恢复 `drag-canvas`
- 边点击事件：非连线模式下点击边 → 获取边的 `source`/`target`，在 `nodeTableMap` 中查找对应表，调用 `setEditRelation`
- 选中节点高亮：通过 `graph.updateNodeData` 修改节点样式

#### 组件树

```
ConversationLineageModal
├── 工具栏 (关联表管理 + 连线模式开关 + 操作按钮)
├── G6 图容器
├── Empty 提示
└── RelationEditModal (内联 Ant Design Modal)
    ├── 关系类型 Radio
    ├── 字段映射行 (动态列表)
    ├── 描述 TextArea
    └── 底部按钮 (保存/删除/取消)
```

### 数据结构

```typescript
// 弹窗用
interface EditRelationState {
  sourceTableId: number;
  targetTableId: number;
  relationType: string;
  description: string;
  columnPairs: { sourceColumnId: number | undefined; targetColumnId: number | undefined }[];
  existingRelationId?: number; // 编辑已有关系时存在
}
```

### 注意事项

- 连线模式下 `drag-canvas` 通过 `graph.updateBehavior('drag-canvas', { enable: false })` 禁用；恢复时 `graph.updateBehavior('drag-canvas', { enable: true })`
- 连线模式下节点点击通过 `graph.on('node:click', handler)` 监听，非连线模式移除监听或加条件判断
- 边点击通过 `graph.on('edge:click', handler)` 监听，仅在非连线模式下触发编辑弹窗
- 字段映射 Select 的选项根据 `sourceTableId`/`targetTableId` 动态过滤，只显示对应表的字段；数据源来自图渲染时缓存的 `node.columns`
- 保存成功后调用 `message.success`，重新请求 `lineageGraph(selectedIds)` 刷新图，弹窗自动关闭、选中态清除
- 刷新图时保持当前的 `selectedIds` 不变
