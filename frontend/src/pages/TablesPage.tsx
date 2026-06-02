import { useState, useEffect } from "react";
import { Table, Button, Segmented, Upload, Modal, Form, Input, message, Space, Popconfirm } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import type { UploadFile } from "antd";
import { listTables, importTable, deleteTable } from "../api/tables";
import type { SourceTable } from "../api/types";

export function TablesPage() {
  const [scope, setScope] = useState<"public" | "private">("public");
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      setTables(await listTables(scope));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope]);

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
    load();
  };

  const columns = [
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

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: "space-between", width: "100%" }}>
        <Segmented
          value={scope}
          onChange={(v) => setScope(v as "public" | "private")}
          options={[
            { label: "公共表库", value: "public" },
            { label: "我的私有表", value: "private" },
          ]}
        />
        <Button type="primary" onClick={() => setImportOpen(true)}>+ 导入 CSV</Button>
      </Space>
      <Table rowKey="id" loading={loading} dataSource={tables} columns={columns} />

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
    </div>
  );
}
