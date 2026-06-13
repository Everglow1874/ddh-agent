import { useRef, useEffect, useState } from "react";
import { Modal, Tooltip, Tag, Typography, Badge } from "antd";
import type { TableDetailOut } from "../../api/types";

const { Text } = Typography;

interface Relation {
  fromTableId: number;
  fromCol: string;
  toTableId: number;
  toCol: string;
  type: "1:1" | "1:N";
}

interface NodeRect {
  tableId: number;
  x: number;
  y: number;
  w: number;
  h: number;
}

const NODE_W = 180;
const NODE_H_BASE = 60; // header height; body grows with cols
const COL_H = 20;
const GAP_X = 80;
const GAP_Y = 40;
const COLS_PER_ROW = 3;

function inferRelations(tables: TableDetailOut[]): Relation[] {
  const nameToId: Record<string, number> = {};
  tables.forEach((t) => {
    nameToId[t.name.toLowerCase()] = t.id;
    // also try without common prefixes/suffixes
    const parts = t.name.toLowerCase().split("_");
    if (parts.length > 1) nameToId[parts.join("_")] = t.id;
  });

  const relations: Relation[] = [];
  const seen = new Set<string>();

  for (const tbl of tables) {
    for (const col of tbl.columns) {
      if (!col.column_name.toLowerCase().endsWith("_id")) continue;
      const refName = col.column_name.toLowerCase().slice(0, -3); // strip "_id"
      const refId = nameToId[refName];
      if (refId && refId !== tbl.id) {
        const key = `${refId}-${tbl.id}`;
        if (!seen.has(key)) {
          seen.add(key);
          relations.push({
            fromTableId: refId,
            fromCol: "id",
            toTableId: tbl.id,
            toCol: col.column_name,
            type: "1:N",
          });
        }
      }
    }
  }
  return relations;
}

function nodeHeight(t: TableDetailOut) {
  return NODE_H_BASE + t.columns.length * COL_H + 12;
}

function layoutNodes(tables: TableDetailOut[]): NodeRect[] {
  const rects: NodeRect[] = [];
  tables.forEach((t, i) => {
    const col = i % COLS_PER_ROW;
    const row = Math.floor(i / COLS_PER_ROW);
    const x = col * (NODE_W + GAP_X) + 24;
    // compute y based on max height in previous rows
    let y = GAP_Y;
    for (let r = 0; r < row; r++) {
      const rowTables = tables.slice(r * COLS_PER_ROW, (r + 1) * COLS_PER_ROW);
      const maxH = Math.max(...rowTables.map(nodeHeight));
      y += maxH + GAP_Y;
    }
    rects.push({ tableId: t.id, x, y, w: NODE_W, h: nodeHeight(t) });
  });
  return rects;
}

function centerOf(rect: NodeRect) {
  return { cx: rect.x + rect.w / 2, cy: rect.y + rect.h / 2 };
}

interface TableNodeProps {
  table: TableDetailOut;
  rect: NodeRect;
}

function TableNode({ table, rect }: TableNodeProps) {
  const content = (
    <div style={{ maxWidth: 280 }}>
      <div style={{ fontWeight: 600, marginBottom: 4 }}>{table.name}</div>
      {table.description && <div style={{ color: "#888", fontSize: 12, marginBottom: 6 }}>{table.description}</div>}
      <table style={{ width: "100%", fontSize: 12, borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ color: "#888" }}>
            <th style={{ textAlign: "left", paddingRight: 8, fontWeight: 500 }}>字段</th>
            <th style={{ textAlign: "left", paddingRight: 8, fontWeight: 500 }}>类型</th>
            <th style={{ textAlign: "left", fontWeight: 500 }}>备注</th>
          </tr>
        </thead>
        <tbody>
          {table.columns.map((c) => (
            <tr key={c.id}>
              <td style={{ paddingRight: 8, paddingTop: 2 }}>{c.column_name}</td>
              <td style={{ paddingRight: 8, paddingTop: 2, color: "#4361ee" }}>{c.data_type}</td>
              <td style={{ paddingTop: 2, color: "#888" }}>{c.comment ?? "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );

  return (
    <Tooltip title={content} placement="right" overlayStyle={{ maxWidth: 320 }}>
      <foreignObject x={rect.x} y={rect.y} width={rect.w} height={rect.h} style={{ overflow: "visible" }}>
        <div
          style={{
            width: rect.w,
            height: rect.h,
            background: "#fff",
            border: "1.5px solid #4361ee",
            borderRadius: 8,
            boxShadow: "0 2px 8px rgba(67,97,238,0.10)",
            padding: "8px 10px",
            boxSizing: "border-box",
            cursor: "default",
          }}
        >
          <div style={{ fontWeight: 600, fontSize: 13, color: "#1a2a4a", marginBottom: 4, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
            {table.name}
          </div>
          <div style={{ borderTop: "1px solid #e8eef8", paddingTop: 4 }}>
            {table.columns.map((c) => (
              <div key={c.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#555", lineHeight: "20px" }}>
                <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: 100 }}>{c.column_name}</span>
                <Tag color="geekblue" style={{ fontSize: 10, padding: "0 4px", lineHeight: "18px", marginRight: 0 }}>{c.data_type}</Tag>
              </div>
            ))}
          </div>
        </div>
      </foreignObject>
    </Tooltip>
  );
}

interface EdgeProps {
  from: NodeRect;
  to: NodeRect;
  rel: Relation;
}

function Edge({ from, to, rel }: EdgeProps) {
  const f = centerOf(from);
  const t = centerOf(to);
  const mx = (f.cx + t.cx) / 2;
  const path = `M ${f.cx} ${f.cy} C ${mx} ${f.cy}, ${mx} ${t.cy}, ${t.cx} ${t.cy}`;
  const lx = mx;
  const ly = (f.cy + t.cy) / 2;
  return (
    <g>
      <path d={path} stroke="#4361ee" strokeWidth={1.5} fill="none" strokeDasharray="4 3" markerEnd="url(#arrow)" />
      <rect x={lx - 14} y={ly - 10} width={28} height={18} rx={4} fill="#eef1ff" />
      <text x={lx} y={ly + 4} textAnchor="middle" fontSize={10} fill="#4361ee" fontWeight={600}>{rel.type}</text>
    </g>
  );
}

interface Props {
  open: boolean;
  tables: TableDetailOut[];
  onClose: () => void;
}

export function SourceTableGraph({ open, tables, onClose }: Props) {
  const rects = layoutNodes(tables);
  const relations = inferRelations(tables);

  const totalW = Math.min(COLS_PER_ROW, tables.length) * (NODE_W + GAP_X) + GAP_X;
  const rows = Math.ceil(tables.length / COLS_PER_ROW);
  let totalH = GAP_Y;
  for (let r = 0; r < rows; r++) {
    const rowTables = tables.slice(r * COLS_PER_ROW, (r + 1) * COLS_PER_ROW);
    const maxH = rowTables.length > 0 ? Math.max(...rowTables.map(nodeHeight)) : 0;
    totalH += maxH + GAP_Y;
  }

  const rectById = Object.fromEntries(rects.map((r) => [r.tableId, r]));

  return (
    <Modal
      title={`源表关联关系图（共 ${tables.length} 张表，hover 查看字段详情）`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={Math.max(totalW + 48, 600)}
      styles={{ body: { overflowX: "auto", padding: 16 } }}
    >
      {tables.length === 0 ? (
        <div style={{ textAlign: "center", color: "#888", padding: 32 }}>本次对话未选择源表</div>
      ) : (
        <svg width={totalW} height={totalH} style={{ display: "block" }}>
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
              <path d="M0,0 L0,6 L8,3 z" fill="#4361ee" />
            </marker>
          </defs>
          {relations.map((rel, i) => {
            const fromRect = rectById[rel.fromTableId];
            const toRect = rectById[rel.toTableId];
            if (!fromRect || !toRect) return null;
            return <Edge key={i} from={fromRect} to={toRect} rel={rel} />;
          })}
          {tables.map((t) => {
            const rect = rectById[t.id];
            if (!rect) return null;
            return <TableNode key={t.id} table={t} rect={rect} />;
          })}
        </svg>
      )}
      <div style={{ marginTop: 12, fontSize: 12, color: "#888" }}>
        <Text type="secondary">箭头表示外键引用方向（1:N 关系自动推断），hover 表格节点可查看字段详情。</Text>
      </div>
    </Modal>
  );
}
