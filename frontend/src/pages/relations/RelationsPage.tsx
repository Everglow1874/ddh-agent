import { useState, useEffect, useCallback } from "react";
import { Table, Button, Space, Tag, Popconfirm, Checkbox, message } from "antd";
import { listTables } from "../../api/tables";
import { listRelations, deleteRelation } from "../../api/relations";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { Relation, SourceTable } from "../../api/types";
import { RelationEditModal } from "./RelationEditModal";
import { LineageGraphModal } from "./LineageGraphModal";

export function RelationsPage() {
  const [relations, setRelations] = useState<Relation[]>([]);
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Relation | null>(null);
  const [checked, setChecked] = useState<number[]>([]);
  const [graphOpen, setGraphOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [rel, tbs] = await Promise.all([listRelations(), listTables("all")]);
      setRelations(rel);
      setTables(tbs);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const onDelete = async (id: number) => {
    await deleteRelation(id);
    message.success("已删除");
    load();
  };

  const columns = [
    {
      title: "主表 → 关联表",
      key: "tables",
      render: (_: unknown, r: Relation) => (
        <span>
          <b>{r.source_table_name}</b>
          {r.source_table_comment && <span style={{ color: "#999", fontSize: 12 }}>({r.source_table_comment})</span>}
          {" → "}
          <b>{r.target_table_name}</b>
          {r.target_table_comment && <span style={{ color: "#999", fontSize: 12 }}>({r.target_table_comment})</span>}
        </span>
      ),
    },
    {
      title: "关系类型",
      dataIndex: "relation_type",
      key: "relation_type",
      width: 100,
      render: (t: string) => <Tag color="blue">{RELATION_TYPE_LABELS[t] || t}</Tag>,
    },
    {
      title: "关联字段",
      key: "pairs",
      render: (_: unknown, r: Relation) => (
        <span style={{ fontSize: 12 }}>
          {r.column_pairs.map((p, i) => (
            <Tag key={i}>
              {p.source_column_name} = {p.target_column_name}
            </Tag>
          ))}
        </span>
      ),
    },
    { title: "说明", dataIndex: "description", key: "description" },
    {
      title: "操作",
      key: "action",
      width: 120,
      render: (_: unknown, r: Relation) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(r);
              setEditOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除?" onConfirm={() => onDelete(r.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          onClick={() => {
            setEditing(null);
            setEditOpen(true);
          }}
        >
          + 新建关系
        </Button>
        <Button disabled={checked.length < 1} onClick={() => setGraphOpen(true)}>
          展示血缘图(已选 {checked.length})
        </Button>
      </Space>

      <div style={{ marginBottom: 8 }}>
        <Checkbox.Group
          value={checked}
          onChange={(v) => setChecked(v as number[])}
          options={tables.map((t) => ({ label: t.name, value: t.id }))}
        />
      </div>

      <Table dataSource={relations} columns={columns} rowKey="id" size="small" loading={loading} pagination={false} />

      <RelationEditModal
        open={editOpen}
        tables={tables}
        editing={editing}
        onClose={() => setEditOpen(false)}
        onSaved={load}
      />
      <LineageGraphModal open={graphOpen} tableIds={checked} onClose={() => setGraphOpen(false)} />
    </div>
  );
}
