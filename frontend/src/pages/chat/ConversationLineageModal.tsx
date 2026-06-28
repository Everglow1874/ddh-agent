import { useEffect, useRef, useState } from "react";
import { Modal, Button, Space, message, Select, Tag, Empty, Typography } from "antd";
import { PlusOutlined, CloseOutlined } from "@ant-design/icons";
import { Graph } from "@antv/g6";
import type { IElementEvent, ElementDatum } from "@antv/g6";
import { lineageGraph } from "../../api/relations";
import { listTables } from "../../api/tables";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { LineageGraph, GraphNode, SourceTable } from "../../api/types";

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

  useEffect(() => {
    if (open) {
      listTables("all").then(setAllTables);
    }
  }, [open]);

  useEffect(() => {
    if (open) {
      setSelectedIds(tableIds);
      setAddValue(null);
    }
  }, [open, tableIds]);

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
    };

    render();
    return () => {
      disposed = true;
      graphRef.current?.destroy();
      graphRef.current = null;
    };
  }, [open, selectedIds]);

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

  return (
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
          />
          <Button icon={<PlusOutlined />} disabled={addValue == null} onClick={() => {
            if (addValue != null) {
              setSelectedIds((prev) => [...prev, addValue]);
              setAddValue(null);
            }
          }}>添加</Button>
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
  );
}
