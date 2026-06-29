import { useEffect, useRef, useState } from "react";
import { Modal, Button, Space, message, Select, Tag, Empty, Typography, Switch, Radio, Input } from "antd";
import { PlusOutlined, CloseOutlined } from "@ant-design/icons";
import { Graph } from "@antv/g6";
import type { IElementEvent, ElementDatum } from "@antv/g6";
import { lineageGraph, createRelation, updateRelation, deleteRelation } from "../../api/relations";
import { listTables } from "../../api/tables";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { LineageGraph, GraphNode, SourceTable, GraphColumn, GraphEdge } from "../../api/types";

interface Props {
  open: boolean;
  onClose: () => void;
  tableIds: number[];
  onSave?: (tableIds: number[]) => void;
}

function columnsHtml(node: GraphNode): string {
  const rows = node.columns
    .map((c) => {
      const comment = c.comment ? `<span style="color:#999"> ${c.comment}</span>` : "";
      return `<div style="font-size:12px;line-height:18px"><b>${c.name}</b> <span style="color:#888">${c.type}</span>${comment}</div>`;
    })
    .join("");
  return `<div style="max-width:280px"><div style="font-weight:600;color:#1677ff;margin-bottom:4px">${node.table_name}${
    node.table_comment ? " (" + node.table_comment + ")" : ""
  }</div>${rows}</div>`;
}

export function ConversationLineageModal({ open, onClose, tableIds, onSave }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const dataRef = useRef<LineageGraph | null>(null);
  const [allTables, setAllTables] = useState<SourceTable[]>([]);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [addValue, setAddValue] = useState<number | null>(null);
  const [lineMode, setLineMode] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [, setSelectedPair] = useState<[string, string] | null>(null);
  const lineModeRef = useRef(false);
  const selectedNodeIdRef = useRef<string | null>(null);
  const nodeTableMapRef = useRef<Map<string, { id: number; name: string; columns: GraphColumn[] }>>(new Map());

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
  const [graphKey, setGraphKey] = useState(0);

  const getRelationId = (edge: GraphEdge): number | undefined => edge.relation_id;

  useEffect(() => {
    if (open) {
      listTables("all").then(setAllTables);
    }
  }, [open]);

  useEffect(() => {
    if (open) {
      setSelectedIds(tableIds);
      setAddValue(null);
      setSelectedNodeId(null);
      setSelectedPair(null);
      setLineMode(false);
    }
  }, [open, tableIds]);

  useEffect(() => {
    lineModeRef.current = lineMode;
  }, [lineMode]);

  useEffect(() => {
    selectedNodeIdRef.current = selectedNodeId;
  }, [selectedNodeId]);

  const tableMap = new Map(allTables.map((t) => [t.id, t]));
  const selectedTables = selectedIds.map((id) => tableMap.get(id)).filter(Boolean) as SourceTable[];

  const addableOptions = allTables
    .filter((t) => !selectedIds.includes(t.id))
    .map((t) => ({ label: `${t.name} (${t.scope === 1 ? "公共" : "私有"})`, value: t.id }));

  useEffect(() => {
    if (!open || !containerRef.current || selectedIds.length === 0) return;
    let disposed = false;

    const render = async () => {
      let data: LineageGraph;
      try {
        data = await lineageGraph(selectedIds);
      } catch {
        message.error("获取血缘图失败");
        return;
      }
      if (disposed || !containerRef.current) return;
      dataRef.current = data;

      // 缓存节点表信息供字段映射使用
      const map = new Map<string, { id: number; name: string; columns: GraphColumn[] }>();
      data.nodes.forEach((n) => {
        const tableId = Number(n.id.replace("t_", ""));
        const t = allTables.find((x) => x.id === tableId);
        map.set(n.id, { id: tableId, name: t?.name ?? n.table_name, columns: n.columns });
      });
      nodeTableMapRef.current = map;

      const nodeById = new Map<string, GraphNode>(data.nodes.map((n) => [n.id, n]));

      const graph = new Graph({
        container: containerRef.current,
        autoFit: "view",
        data: {
          nodes: data.nodes.map((n) => ({
            id: n.id,
            data: { label: n.table_comment ? `${n.table_name}\n${n.table_comment}` : n.table_name },
          })),
          edges: data.edges.map((e, i) => ({
            id: `e_${i}`,
            source: e.source,
            target: e.target,
            data: { label: RELATION_TYPE_LABELS[e.relation_type] || e.relation_type },
          })),
        },
        node: {
          type: "rect",
          style: {
            size: [150, 50],
            radius: 8,
            fill: "#ffffff",
            stroke: "#1677ff",
            lineWidth: 1.5,
            labelText: (d: { data?: Record<string, unknown> }) => String(d.data?.label ?? ""),
            labelFill: "#1677ff",
            labelFontSize: 13,
            labelFontWeight: 600,
            labelPlacement: "center",
          },
        },
        edge: {
          type: "polyline",
          style: {
            stroke: "#1677ff",
            lineWidth: 1.5,
            endArrow: true,
            labelText: (d: { data?: Record<string, unknown> }) => String(d.data?.label ?? ""),
            labelFill: "#888",
            labelBackground: true,
            labelBackgroundFill: "#fff",
          },
        },
        layout: { type: "dagre", rankdir: "LR", nodesep: 30, ranksep: 70 },
        behaviors: ["drag-canvas", "zoom-canvas", "drag-element"],
        plugins: [
          {
            type: "tooltip",
            key: "tooltip",
            trigger: "hover",
            getContent: (_event: IElementEvent, items: ElementDatum[]) => {
              const rawId = items?.[0]?.id;
              const id = rawId != null ? String(rawId) : undefined;
              const n = id ? nodeById.get(id) : undefined;
              return Promise.resolve(n ? columnsHtml(n) : "");
            },
          },
        ],
      });
      graphRef.current = graph;
      await graph.render();

      // 连线模式：节点点击
      graph.on("node:click", (event: any) => {
        if (!lineModeRef.current) return;
        const clickedId = event.target?.id;
        if (!clickedId) return;

        const curSelected = selectedNodeIdRef.current;
        if (curSelected === null) {
          syncNodeStyles(graph, [clickedId]);
          setSelectedPair(null);
          setSelectedNodeId(clickedId);
        } else if (curSelected === clickedId) {
          syncNodeStyles(graph, []);
          setSelectedNodeId(null);
          setSelectedPair(null);
        } else {
          const sourceId = curSelected;
          syncNodeStyles(graph, [sourceId], clickedId);
          const targetId = clickedId;
          setSelectedPair([sourceId, targetId]);
          setSelectedNodeId(null);

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
        }
      });

      // 非连线模式：边点击编辑
      graph.on("edge:click", (event: any) => {
        if (lineModeRef.current) return;
        const edgeId = event.target?.id;
        syncNodeStyles(graph, []);
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
    };

    render();
    return () => {
      disposed = true;
      graphRef.current?.destroy();
      graphRef.current = null;
    };
  }, [open, selectedIds, graphKey]);

  const handleExportImage = async () => {
    if (!graphRef.current) return;
    try {
      const url = await graphRef.current.toDataURL({ type: "image/png" });
      const a = document.createElement("a");
      a.href = url;
      a.download = "表血缘图.png";
      a.click();
    } catch {
      message.error("导出图片失败");
    }
  };

  const handleExportJson = () => {
    if (!dataRef.current) return;
    const blob = new Blob([JSON.stringify(dataRef.current, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "表血缘图.json";
    a.click();
    URL.revokeObjectURL(url);
  };

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
      setSelectedNodeId(null);
      setSelectedPair(null);
      setGraphKey((k) => k + 1);
    } catch {
      message.error("保存关系失败");
    }
  };

  const handleCancelEditRelation = () => {
    const graph = graphRef.current;
    if (graph) syncNodeStyles(graph, []);
    setEditRelation(null);
    setSelectedPair(null);
  };

  const handleDeleteRelation = async () => {
    if (!editRelation?.existingRelationId) return;
    try {
      await deleteRelation(editRelation.existingRelationId);
      message.success("关系已删除");
      setEditRelation(null);
      setSelectedNodeId(null);
      setSelectedPair(null);
      setGraphKey((k) => k + 1);
    } catch {
      message.error("删除关系失败");
    }
  };

  const syncNodeStyles = (graph: Graph, highlight: string[], second?: string) => {
    const allNodeIds = [...nodeTableMapRef.current.keys()];
    allNodeIds.forEach((id) => {
      const isHighlight = highlight.includes(id);
      const isSecond = second === id;
      graph.updateNodeData([{
        id,
        style: {
          stroke: isSecond ? "#52c41a" : isHighlight ? "#ff7a00" : "#1677ff",
          lineWidth: isHighlight || isSecond ? 3 : 1.5,
        },
      }]);
    });
    graph.draw();
  };

  return (
    <>
    <Modal
      title="表血缘图"
      open={open}
      onCancel={onClose}
      footer={null}
      width="86%"
      style={{ top: 24 }}
      styles={{ body: { padding: 0 } }}
    >
      <div style={{ padding: "8px 16px", borderBottom: "1px solid #f0f0f0" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
          <Typography.Text strong style={{ fontSize: 13 }}>关联表管理</Typography.Text>
          <Space>
            <Button size="small" type="primary" disabled={selectedIds.length === 0} onClick={() => onSave?.(selectedIds)}>保存</Button>
            <Button size="small" disabled={selectedIds.length === 0} onClick={handleExportImage}>导出图片</Button>
            <Button size="small" disabled={selectedIds.length === 0} onClick={handleExportJson}>导出 JSON</Button>
          </Space>
        </div>
        <div style={{ marginBottom: 8 }}>
          {selectedTables.length > 0 ? (
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
              {selectedTables.map((t) => (
                <Tag
                  key={t.id}
                  closable
                  onClose={() => setSelectedIds((prev) => prev.filter((id) => id !== t.id))}
                  color={t.scope === 1 ? "blue" : "green"}
                  style={{ fontSize: 12, lineHeight: "22px", padding: "0 4px 0 8px", margin: 0 }}
                >
                  {t.name}
                </Tag>
              ))}
            </div>
          ) : (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>尚未关联任何表</Typography.Text>
          )}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Select
            style={{ flex: 1, minWidth: 0 }}
            placeholder="搜索并添加表..."
            value={addValue}
            onChange={(val) => {
              if (val != null) {
                setSelectedIds((prev) => [...prev, val]);
                setAddValue(null);
              }
            }}
            showSearch
            filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
            options={addableOptions}
            notFoundContent="无可用表"
            disabled={lineMode}
          />
          <Button icon={<PlusOutlined />} disabled={lineMode || addValue == null} onClick={() => {
            if (addValue != null) {
              setSelectedIds((prev) => [...prev, addValue]);
              setAddValue(null);
            }
          }}>添加</Button>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, paddingTop: 8, borderTop: "1px solid #f0f0f0", marginTop: 8 }}>
          <Switch size="small" checked={lineMode} onChange={(v) => { setLineMode(v); setSelectedNodeId(null); setSelectedPair(null); }} />
          <span style={{ fontSize: 12, color: "#666" }}>连线模式</span>
          {lineMode && <Tag color="orange" style={{ fontSize: 10, lineHeight: "16px", margin: 0 }}>点击两个节点连线</Tag>}
        </div>
      </div>
      {selectedIds.length === 0 ? (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "70vh" }}>
          <Empty description="请在上方选择至少一个表" />
        </div>
      ) : (
        <div ref={containerRef} style={{ width: "100%", height: "70vh" }} />
      )}
    </Modal>
      <Modal
        title={editRelation ? `${editRelation.sourceTable.name} ←→ ${editRelation.targetTable.name}` : "编辑关系"}
        open={editRelation !== null}
        onCancel={handleCancelEditRelation}
        width={520}
        footer={null}
        destroyOnClose
      >
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
                getPopupContainer={(trigger) => trigger.parentElement!}
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
                getPopupContainer={(trigger) => trigger.parentElement!}
              />
              <Button type="text" size="small" danger icon={<CloseOutlined />} onClick={() => {
                const newPairs = editRelation.columnPairs.filter((_, j) => j !== i);
                setEditRelation({ ...editRelation, columnPairs: newPairs });
              }} />
            </div>
          ))}
        </div>
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>描述（可选）</div>
          <Input.TextArea
            rows={2}
            value={editRelation?.description ?? ""}
            onChange={(e) => setEditRelation((prev) => prev ? { ...prev, description: e.target.value } : null)}
            placeholder="描述该关系的业务含义..."
          />
        </div>
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
    </>
  );
}
