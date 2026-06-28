import { useEffect, useRef, useState, useMemo } from "react";
import { Modal, Button, Space, message, Select, Tag, Empty } from "antd";
import { Graph } from "@antv/g6";
import type { IElementEvent, ElementDatum } from "@antv/g6";
import { lineageGraph } from "../../api/relations";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { LineageGraph, GraphNode, SourceTable } from "../../api/types";

interface Props {
  open: boolean;
  tables: SourceTable[];
  onClose: () => void;
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

export function LineageGraphModal({ open, tables, onClose }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const dataRef = useRef<LineageGraph | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [search, setSearch] = useState("");

  const tableOptions = useMemo(() => tables.map((t) => ({
    label: (
      <Space size={4}>
        <span>{t.name}</span>
        <Tag color={t.scope === 1 ? "blue" : "green"} style={{ fontSize: 10, lineHeight: "16px", padding: "0 4px" }}>
          {t.scope === 1 ? "公共" : "私有"}
        </Tag>
      </Space>
    ),
    value: t.id,
    searchText: `${t.name} ${t.scope === 1 ? "公共" : "私有"} ${t.description ?? ""}`,
  })), [tables]);

  const displayOptions = useMemo(() => {
    if (!search) return tableOptions.slice(0, 10);
    const q = search.toLowerCase();
    return tableOptions.filter((o) => o.searchText.toLowerCase().includes(q));
  }, [tableOptions, search]);

  useEffect(() => {
    if (open) {
      setSelectedIds([]);
      setSearch("");
    }
  }, [open]);

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
      <div style={{ padding: "8px 16px", borderBottom: "1px solid #f0f0f0", display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <span style={{ fontSize: 13, color: "#666", whiteSpace: "nowrap" }}>选择表：</span>
        <Select
          mode="multiple"
          style={{ minWidth: 320, maxWidth: 500 }}
          placeholder="搜索并选择表（显示前10条，搜索展开更多）"
          value={selectedIds}
          onChange={setSelectedIds}
          showSearch
          filterOption={false}
          onSearch={setSearch}
          onDropdownVisibleChange={(visible) => { if (!visible) setSearch(""); }}
          notFoundContent={search ? "未找到匹配的表" : "暂无数据"}
          options={displayOptions}
          tagRender={(props) => {
            const t = tables.find((x) => x.id === props.value);
            return (
              <Tag closable={props.closable} onClose={props.onClose} color={t?.scope === 1 ? "blue" : "green"} style={{ marginRight: 3 }}>
                {props.label}
              </Tag>
            );
          }}
        />
        <Space>
          <Button size="small" disabled={selectedIds.length === 0} onClick={handleExportImage}>导出图片</Button>
          <Button size="small" disabled={selectedIds.length === 0} onClick={handleExportJson}>导出 JSON</Button>
        </Space>
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