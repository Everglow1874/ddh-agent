import { useState, useEffect, useCallback } from "react";
import { Table, Button, Segmented, Upload, Modal, Form, Input, message, Space, Popconfirm, Tag, Checkbox } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import type { UploadFile } from "antd";
import { listTables, importTable, deleteTable } from "../api/tables";
import { listRelations, deleteRelation } from "../api/relations";
import { RELATION_TYPE_LABELS } from "../api/types";
import type { SourceTable, Relation } from "../api/types";
import { RelationEditModal } from "./relations/RelationEditModal";
import { LineageGraphModal } from "./relations/LineageGraphModal";

type TabScope = "public" | "private" | "relations";

export function TablesPage() {
  const [scope, setScope] = useState<TabScope>("public");
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm();

  const [relations, setRelations] = useState<Relation[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Relation | null>(null);
  const [checked, setChecked] = useState<number[]>([]);
  const [graphOpen, setGraphOpen] = useState(false);

  const loadTables = async () => {
    setLoading(true);
    try {
      setTables(await listTables(scope as "public" | "private"));
    } finally {
      setLoading(false);
    }
  };

  const loadRelations = useCallback(async () => {
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
    if (scope === "relations") {
      loadRelations();
    } else {
      loadTables();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope, loadRelations]);

  const onImport = async (values: { description?: string }) => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.error("请选择 CSV 文件");
      return;
    }
    try {
      await importTable(file as File, scope === "public" ? 1 : 2, values.description);
      message.success("导入成功");
      setImportOpen(false);
      setFileList([]);
      form.resetFields();
      load();
    } catch (e: unknown) {
      const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      message.error(detail ?? "导入失败");
    }
  };

  const onDelete = async (id: number) => {
    await deleteTable(id);
    message.success("已删除");
    loadTables();
  };

  const onRelationDelete = async (id: number) => {
    await deleteRelation(id);
    message.success("已删除");
    loadRelations();
  };

  const tableColumns = [
    { title: "表名", dataIndex: "name", key: "name" },
    { title: "描述", dataIndex: "description", key: "description", render: (v: string | null) => v ?? "-" },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: SourceTable) => (
        <Popconfirm title="确认删除该表？" onConfirm={() => onDelete(record.id)}>
          <Button type="link" danger>删除</Button>
        </Popconfirm>
      ),
    },
  ];

  const relationColumns = [
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
          <Popconfirm title="确认删除?" onConfirm={() => onRelationDelete(r.id)}>
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
      <Space style={{ marginBottom: 16, justifyContent: "space-between", width: "100%" }}>
        <Segmented
          value={scope}
          onChange={(v) => setScope(v as TabScope)}
          options={[
            { label: "公共表库", value: "public" },
            { label: "我的私有表", value: "private" },
            { label: "表关系", value: "relations" },
          ]}
        />
        {scope !== "relations" && (
          <Button type="primary" onClick={() => setImportOpen(true)}>+ 导入 CSV</Button>
        )}
        {scope === "relations" && (
          <Space>
            <Button
              disabled={checked.length < 1}
              onClick={() => setGraphOpen(true)}
            >
              展示血缘图(已选 {checked.length})
            </Button>
            <Button
              type="primary"
              onClick={() => {
                setEditing(null);
                setEditOpen(true);
              }}
            >
              + 新建关系
            </Button>
          </Space>
        )}
      </Space>

      {scope === "relations" && (
        <div style={{ marginBottom: 8 }}>
          <Checkbox.Group
            value={checked}
            onChange={(v) => setChecked(v as number[])}
            options={tables.map((t) => ({ label: t.name, value: t.id }))}
          />
        </div>
      )}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={scope === "relations" ? relations : tables}
        columns={scope === "relations" ? relationColumns : tableColumns}
      />

      {scope !== "relations" && (
        <Modal title="导入 CSV" open={importOpen} onCancel={() => setImportOpen(false)} onOk={() => form.submit()}>
          <Form form={form} onFinish={onImport} layout="vertical">
            <Form.Item label="CSV 文件" required>
              <Upload
                beforeUpload={() => false}
                fileList={fileList}
                onChange={({ fileList: fl }) => setFileList(fl.slice(-1))}
                accept=".csv"
              >
                <Button icon={<UploadOutlined />}>选择文件</Button>
              </Upload>
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} />
            </Form.Item>
          </Form>
        </Modal>
      )}

      <RelationEditModal
        open={editOpen}
        tables={tables}
        editing={editing}
        onClose={() => setEditOpen(false)}
        onSaved={loadRelations}
      />
      <LineageGraphModal open={graphOpen} tableIds={checked} onClose={() => setGraphOpen(false)} />
    </div>
  );
}
