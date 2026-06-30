import { useState, useEffect, useCallback } from "react";
import { Table, Button, Segmented, Upload, Modal, Form, Input, InputNumber, Select, message, Space, Popconfirm, Tag } from "antd";
import { UploadOutlined, SearchOutlined, DownloadOutlined } from "@ant-design/icons";
import type { UploadFile } from "antd";
import { listTables, listTablesPage, importTable, downloadTemplate, deleteTable, getTable, updateTable, addColumn, updateColumn, deleteColumn } from "../api/tables";
import type { ColumnRequest } from "../api/tables";
import { listRelationsPage, deleteRelation } from "../api/relations";
import { RELATION_TYPE_LABELS } from "../api/types";
import type { SourceTable, Relation, TableDetail, TableColumn } from "../api/types";
import { RelationEditModal } from "./relations/RelationEditModal";
import { LineageGraphModal } from "./relations/LineageGraphModal";

const YES_NO = [{ label: "是", value: 1 }, { label: "否", value: 0 }];
const renderBool = (v: number | null) =>
  v === 1 ? <Tag color="blue">是</Tag> : v === 0 ? <Tag>否</Tag> : "-";

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

  const [viewOpen, setViewOpen] = useState(false);
  const [viewTable, setViewTable] = useState<TableDetail | null>(null);
  const [viewLoading, setViewLoading] = useState(false);
  const [tableForm] = Form.useForm();

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
    setViewOpen(true);
    try {
      setViewTable(await getTable(id));
    } catch {
      message.error("获取详情失败");
    } finally {
      setViewLoading(false);
    }
  };

  const onTableInfoSave = async (values: { name: string; description: string }) => {
    if (!viewTable) return;
    try {
      await updateTable(viewTable.id, values);
      message.success("表信息已更新");
      setViewTable({ ...viewTable, name: values.name, description: values.description });
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

  const onColumnSave = async (values: ColumnRequest) => {
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
          <Button type="link" size="small" onClick={() => onView(record.id)}>查看 / 编辑</Button>
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
        <Modal title="导入表（CSV / Excel）" open={importOpen} onCancel={() => setImportOpen(false)} onOk={() => form.submit()}>
          <div style={{ marginBottom: 12, padding: "8px 12px", background: "#f6f8fc", borderRadius: 8, fontSize: 13 }}>
            <Space size={4} wrap>
              <span style={{ color: "#666" }}>请按 13 列标准模板填写：</span>
              <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => downloadTemplate("xlsx")}>下载 xlsx 模板</Button>
              <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => downloadTemplate("csv")}>下载 csv 模板</Button>
            </Space>
          </div>
          <Form form={form} onFinish={onImport} layout="vertical">
            <Form.Item label="文件（.xlsx / .xls / .csv）" required>
              <Upload
                beforeUpload={() => false}
                fileList={fileList}
                onChange={({ fileList: fl }) => setFileList(fl.slice(-1))}
                accept=".csv,.xlsx,.xls"
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

      <Modal
        title={viewTable ? `表详情 / 编辑 - ${viewTable.name}` : "表详情"}
        open={viewOpen}
        onCancel={() => setViewOpen(false)}
        footer={null}
        width="80%"
        style={{ top: 24 }}
        destroyOnClose
      >
        {viewLoading && <p style={{ color: "#999" }}>加载中…</p>}
        {viewTable && (
          <div>
            <Form form={tableForm} onFinish={onTableInfoSave} layout="vertical"
              initialValues={{ name: viewTable.name, description: viewTable.description ?? "" }}
              style={{ background: "#f6f8fc", padding: "12px 16px", borderRadius: 8, marginBottom: 16 }}>
              <Space style={{ display: "flex" }} align="start">
                <Form.Item name="name" label="表名" rules={[{ required: true, message: "请输入表名" }]} style={{ flex: 1 }}>
                  <Input />
                </Form.Item>
                <Form.Item name="description" label="描述" style={{ flex: 2 }}>
                  <Input />
                </Form.Item>
                <Form.Item label=" ">
                  <Button type="primary" onClick={() => tableForm.submit()}>保存表信息</Button>
                </Form.Item>
              </Space>
            </Form>
            <Space style={{ marginBottom: 8, justifyContent: "space-between", width: "100%" }}>
              <b>字段列表</b>
              <Button type="primary" size="small" onClick={onColumnAdd}>+ 添加字段</Button>
            </Space>
            <Table
              dataSource={viewTable.columns}
              rowKey="id"
              size="small"
              pagination={false}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "序号", dataIndex: "sort_order", key: "sort_order", width: 56 },
                { title: "字段名", dataIndex: "column_name", key: "column_name", fixed: "left" },
                { title: "中文名", dataIndex: "comment", key: "comment", render: (v: string | null) => v ?? "-" },
                { title: "类型", dataIndex: "data_type", key: "data_type" },
                { title: "长度", dataIndex: "col_length", key: "col_length", render: (v: number | null) => v ?? "-" },
                { title: "精度", dataIndex: "col_precision", key: "col_precision", render: (v: number | null) => v ?? "-" },
                { title: "主键", dataIndex: "is_primary_key", key: "is_primary_key", render: renderBool },
                { title: "可空", dataIndex: "is_nullable", key: "is_nullable", render: renderBool },
                { title: "分布键", dataIndex: "is_distribution_key", key: "is_distribution_key", render: renderBool },
                { title: "分区键", dataIndex: "is_partition_key", key: "is_partition_key", render: renderBool },
                { title: "缺省值", dataIndex: "default_value", key: "default_value", render: (v: string | null) => v ?? "-" },
                { title: "代码信息", dataIndex: "code_info", key: "code_info", render: (v: string | null) => v ?? "-" },
                { title: "下游数", dataIndex: "downstream_job_count", key: "downstream_job_count", render: (v: number | null) => v ?? "-" },
                {
                  title: "操作", key: "action", width: 110, fixed: "right",
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
      </Modal>

      <Modal
        title={editingColumn ? "编辑字段" : "添加字段"}
        open={columnModalOpen}
        onCancel={() => { setColumnModalOpen(false); setEditingColumn(null); }}
        onOk={() => columnForm.submit()}
      >
        <Form form={columnForm} onFinish={onColumnSave} layout="vertical">
          <Space style={{ display: "flex" }} align="start">
            <Form.Item name="column_name" label="字段名" rules={[{ required: true, message: "请输入字段名" }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="comment" label="字段中文名" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space style={{ display: "flex" }} align="start">
            <Form.Item name="data_type" label="类型" rules={[{ required: true, message: "请输入数据类型" }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="col_length" label="长度">
              <InputNumber style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="col_precision" label="精度">
              <InputNumber style={{ width: 100 }} />
            </Form.Item>
          </Space>
          <Space style={{ display: "flex" }} align="start" wrap>
            <Form.Item name="is_primary_key" label="主键">
              <Select options={YES_NO} allowClear style={{ width: 80 }} />
            </Form.Item>
            <Form.Item name="is_nullable" label="可空">
              <Select options={YES_NO} allowClear style={{ width: 80 }} />
            </Form.Item>
            <Form.Item name="is_distribution_key" label="分布键">
              <Select options={YES_NO} allowClear style={{ width: 80 }} />
            </Form.Item>
            <Form.Item name="is_partition_key" label="分区键">
              <Select options={YES_NO} allowClear style={{ width: 80 }} />
            </Form.Item>
          </Space>
          <Space style={{ display: "flex" }} align="start">
            <Form.Item name="default_value" label="缺省值" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="downstream_job_count" label="下游作业数">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Form.Item name="code_info" label="代码信息">
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
