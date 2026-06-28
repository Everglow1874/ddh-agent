import { useState, useEffect, useCallback } from "react";
import { Table, Button, Segmented, Upload, Modal, Form, Input, message, Space, Popconfirm, Tag, Drawer } from "antd";
import { UploadOutlined, SearchOutlined } from "@ant-design/icons";
import type { UploadFile } from "antd";
import { listTables, listTablesPage, importTable, deleteTable, getTable, updateTable, addColumn, updateColumn, deleteColumn } from "../api/tables";
import { listRelationsPage, deleteRelation } from "../api/relations";
import { RELATION_TYPE_LABELS } from "../api/types";
import type { SourceTable, Relation, TableDetail, TableColumn } from "../api/types";
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

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState("");

  const [relations, setRelations] = useState<Relation[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Relation | null>(null);
  const [graphOpen, setGraphOpen] = useState(false);

  const [viewDrawerOpen, setViewDrawerOpen] = useState(false);
  const [viewTable, setViewTable] = useState<TableDetail | null>(null);
  const [viewLoading, setViewLoading] = useState(false);

  const [updateOpen, setUpdateOpen] = useState(false);
  const [updateForm] = Form.useForm();
  const [updatingTable, setUpdatingTable] = useState<SourceTable | null>(null);

  const [columnModalOpen, setColumnModalOpen] = useState(false);
  const [editingColumn, setEditingColumn] = useState<TableColumn | null>(null);
  const [columnForm] = Form.useForm();

  const loadTables = async () => {
    setLoading(true);
    try {
      const resp = await listTablesPage(scope as "public" | "private", search, page, pageSize);
      setTables(resp.content);
      setTotal(resp.total);
    } finally {
      setLoading(false);
    }
  };

  const loadRelations = useCallback(async () => {
    setLoading(true);
    try {
      const [relPage, tbs] = await Promise.all([
        listRelationsPage(page, pageSize, search),
        listTables("all"),
      ]);
      setRelations(relPage.content);
      setTotal(relPage.total);
      setTables(tbs);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search]);

  useEffect(() => {
    if (scope === "relations") {
      loadRelations();
    } else {
      loadTables();
    }
  }, [scope, page, pageSize, search]);

  useEffect(() => {
    setPage(1);
  }, [scope, search]);

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
      loadTables();
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

  const onView = async (id: number) => {
    setViewLoading(true);
    setViewDrawerOpen(true);
    try {
      setViewTable(await getTable(id));
    } catch {
      message.error("获取详情失败");
    } finally {
      setViewLoading(false);
    }
  };

  const onUpdateOpen = (record: SourceTable) => {
    setUpdatingTable(record);
    updateForm.setFieldsValue({ name: record.name, description: record.description ?? "" });
    setUpdateOpen(true);
  };

  const onUpdateSave = async (values: { name: string; description: string }) => {
    if (!updatingTable) return;
    try {
      await updateTable(updatingTable.id, values);
      message.success("更新成功");
      setUpdateOpen(false);
      setUpdatingTable(null);
      loadTables();
    } catch {
      message.error("更新失败");
    }
  };

  const onColumnAdd = () => {
    setEditingColumn(null);
    columnForm.resetFields();
    setColumnModalOpen(true);
  };

  const onColumnEdit = (col: TableColumn) => {
    setEditingColumn(col);
    columnForm.setFieldsValue(col);
    setColumnModalOpen(true);
  };

  const onColumnDelete = async (col: TableColumn) => {
    if (!viewTable) return;
    await deleteColumn(viewTable.id, col.id);
    message.success("字段已删除");
    const detail = await getTable(viewTable.id);
    setViewTable(detail);
  };

  const onColumnSave = async (values: { column_name: string; data_type: string; comment?: string }) => {
    if (!viewTable) return;
    const tid = viewTable.id;
    if (editingColumn) {
      await updateColumn(tid, editingColumn.id, values);
      message.success("字段已更新");
    } else {
      await addColumn(tid, values);
      message.success("字段已添加");
    }
    setColumnModalOpen(false);
    setEditingColumn(null);
    const detail = await getTable(tid);
    setViewTable(detail);
  };

  const tableColumns = [
    { title: "表名", dataIndex: "name", key: "name" },
    { title: "描述", dataIndex: "description", key: "description", render: (v: string | null) => v ?? "-" },
    {
      title: "操作",
      key: "action",
      width: 200,
      render: (_: unknown, record: SourceTable) => (
        <Space>
          <Button type="link" size="small" onClick={() => onView(record.id)}>查看</Button>
          <Button type="link" size="small" onClick={() => onUpdateOpen(record)}>编辑</Button>
          <Popconfirm title="确认删除该表？" onConfirm={() => onDelete(record.id)}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
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
          <Space>
            <Input
              placeholder="搜索表名/描述..."
              prefix={<SearchOutlined />}
              allowClear
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: 220 }}
            />
            <Button type="primary" onClick={() => setImportOpen(true)}>+ 导入 CSV</Button>
          </Space>
        )}
        {scope === "relations" && (
          <Space>
            <Input
              placeholder="搜索关系..."
              prefix={<SearchOutlined />}
              allowClear
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: 220 }}
            />
          </Space>
        )}
        {scope === "relations" && (
          <Space>
            <Button onClick={() => setGraphOpen(true)}>
              展示血缘图
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

      <Table
        rowKey="id"
        loading={loading}
        dataSource={(scope === "relations" ? relations : tables) as any}
        columns={(scope === "relations" ? relationColumns : tableColumns) as any}
        pagination={{
          current: page,
          pageSize,
          total,
          onChange: (p, ps) => {
            setPage(p);
            if (ps !== pageSize) setPageSize(ps);
          },
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
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

      <Drawer
        title={viewTable ? `${viewTable.name} - 字段列表` : "表详情"}
        open={viewDrawerOpen}
        onClose={() => setViewDrawerOpen(false)}
        width={580}
        loading={viewLoading}
      >
        {viewTable && (
          <div>
            <p><b>描述：</b>{viewTable.description ?? "-"}</p>
            <Space style={{ marginBottom: 8 }}>
              <Button type="primary" size="small" onClick={onColumnAdd}>+ 添加字段</Button>
            </Space>
            <Table
              dataSource={viewTable.columns}
              rowKey="id"
              size="small"
              pagination={false}
              columns={[
                { title: "字段名", dataIndex: "column_name", key: "column_name" },
                { title: "类型", dataIndex: "data_type", key: "data_type" },
                { title: "注释", dataIndex: "comment", key: "comment", render: (v: string | null) => v ?? "-" },
                {
                  title: "操作", key: "action", width: 120,
                  render: (_: unknown, col: TableColumn) => (
                    <Space>
                      <Button type="link" size="small" onClick={() => onColumnEdit(col)}>编辑</Button>
                      <Popconfirm title="确认删除该字段？" onConfirm={() => onColumnDelete(col)}>
                        <Button type="link" size="small" danger>删除</Button>
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]}
            />
          </div>
        )}
      </Drawer>

      <Modal
        title={editingColumn ? "编辑字段" : "添加字段"}
        open={columnModalOpen}
        onCancel={() => { setColumnModalOpen(false); setEditingColumn(null); }}
        onOk={() => columnForm.submit()}
      >
        <Form form={columnForm} onFinish={onColumnSave} layout="vertical">
          <Form.Item name="column_name" label="字段名" rules={[{ required: true, message: "请输入字段名" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="data_type" label="类型" rules={[{ required: true, message: "请输入数据类型" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="comment" label="注释">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑表信息"
        open={updateOpen}
        onCancel={() => { setUpdateOpen(false); setUpdatingTable(null); }}
        onOk={() => updateForm.submit()}
      >
        <Form form={updateForm} onFinish={onUpdateSave} layout="vertical">
          <Form.Item name="name" label="表名" rules={[{ required: true, message: "请输入表名" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <RelationEditModal
        open={editOpen}
        tables={tables}
        editing={editing}
        onClose={() => setEditOpen(false)}
        onSaved={loadRelations}
      />
      <LineageGraphModal open={graphOpen} tables={tables} onClose={() => setGraphOpen(false)} />
    </div>
  );
}
