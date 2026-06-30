import { useState, useEffect, useCallback } from "react";
import { Tabs, Table, Button, Space, Modal, Form, Input, InputNumber, Select, Tag, Popconfirm, message, Typography } from "antd";
import {
  listTypeRules, createTypeRule, updateTypeRule, deleteTypeRule,
  listFunctionRules, createFunctionRule, updateFunctionRule, deleteFunctionRule,
} from "../api/dialectKb";
import type { DialectTypeRule, DialectFunctionRule } from "../api/types";

const ENABLED_OPTIONS = [{ label: "启用", value: 1 }, { label: "停用", value: 0 }];
const enabledTag = (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>);

export function DialectKbPage() {
  const [types, setTypes] = useState<DialectTypeRule[]>([]);
  const [funcs, setFuncs] = useState<DialectFunctionRule[]>([]);
  const [loading, setLoading] = useState(false);

  const [typeOpen, setTypeOpen] = useState(false);
  const [typeEditing, setTypeEditing] = useState<DialectTypeRule | null>(null);
  const [typeForm] = Form.useForm();

  const [funcOpen, setFuncOpen] = useState(false);
  const [funcEditing, setFuncEditing] = useState<DialectFunctionRule | null>(null);
  const [funcForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [t, f] = await Promise.all([listTypeRules(), listFunctionRules()]);
      setTypes(t);
      setFuncs(f);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // ---- 类型规则 ----
  const openTypeNew = () => { setTypeEditing(null); setTypeOpen(true); };
  const openTypeEdit = (r: DialectTypeRule) => { setTypeEditing(r); setTypeOpen(true); };
  const saveType = async (values: Partial<DialectTypeRule>) => {
    try {
      if (typeEditing) await updateTypeRule(typeEditing.id, values);
      else await createTypeRule(values);
      message.success("已保存");
      setTypeOpen(false);
      load();
    } catch { message.error("保存失败"); }
  };
  const removeType = async (id: number) => { await deleteTypeRule(id); message.success("已删除"); load(); };

  // ---- 函数规则 ----
  const openFuncNew = () => { setFuncEditing(null); setFuncOpen(true); };
  const openFuncEdit = (r: DialectFunctionRule) => { setFuncEditing(r); setFuncOpen(true); };
  const saveFunc = async (values: Partial<DialectFunctionRule>) => {
    try {
      if (funcEditing) await updateFunctionRule(funcEditing.id, values);
      else await createFunctionRule(values);
      message.success("已保存");
      setFuncOpen(false);
      load();
    } catch { message.error("保存失败"); }
  };
  const removeFunc = async (id: number) => { await deleteFunctionRule(id); message.success("已删除"); load(); };

  const typeColumns = [
    { title: "类型名", dataIndex: "type_name", key: "type_name" },
    { title: "允许形态", dataIndex: "allowed_forms", key: "allowed_forms", render: (v: string | null) => v ?? "-" },
    { title: "取整规则", dataIndex: "rounding_rule", key: "rounding_rule", render: (v: string | null) => v ?? "-" },
    { title: "平台写法", dataIndex: "platform_syntax", key: "platform_syntax", render: (v: string | null) => v ?? "-" },
    { title: "说明", dataIndex: "note", key: "note", render: (v: string | null) => v ?? "-" },
    { title: "状态", dataIndex: "enabled", key: "enabled", width: 80, render: enabledTag },
    {
      title: "操作", key: "action", width: 120,
      render: (_: unknown, r: DialectTypeRule) => (
        <Space>
          <Button type="link" size="small" onClick={() => openTypeEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除？" onConfirm={() => removeType(r.id)}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const funcColumns = [
    { title: "函数名", dataIndex: "function_name", key: "function_name" },
    { title: "签名", dataIndex: "signature", key: "signature", render: (v: string | null) => v ?? "-" },
    { title: "语义说明", dataIndex: "description", key: "description", render: (v: string | null) => v ?? "-" },
    { title: "示例", dataIndex: "example", key: "example", render: (v: string | null) => v ?? "-" },
    { title: "状态", dataIndex: "enabled", key: "enabled", width: 80, render: enabledTag },
    {
      title: "操作", key: "action", width: 120,
      render: (_: unknown, r: DialectFunctionRule) => (
        <Space>
          <Button type="link" size="small" onClick={() => openFuncEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除？" onConfirm={() => removeFunc(r.id)}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        平台方言知识库：生成 SQL 时全量注入提示词，让 AI 按本平台方言（合法类型长度、内置函数）生成。
      </Typography.Paragraph>
      <Tabs
        items={[
          {
            key: "type",
            label: "类型规则",
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={openTypeNew}>+ 新增类型规则</Button>
                </Space>
                <Table rowKey="id" size="small" loading={loading} pagination={false}
                  dataSource={types} columns={typeColumns} scroll={{ x: "max-content" }} />
              </>
            ),
          },
          {
            key: "func",
            label: "内部函数",
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={openFuncNew}>+ 新增函数规则</Button>
                </Space>
                <Table rowKey="id" size="small" loading={loading} pagination={false}
                  dataSource={funcs} columns={funcColumns} scroll={{ x: "max-content" }} />
              </>
            ),
          },
        ]}
      />

      <Modal
        title={typeEditing ? "编辑类型规则" : "新增类型规则"}
        open={typeOpen}
        onCancel={() => setTypeOpen(false)}
        onOk={() => typeForm.submit()}
        destroyOnClose
      >
        <Form form={typeForm} layout="vertical" onFinish={saveType}
          initialValues={typeEditing ?? { enabled: 1, sort_order: 0 }}>
          <Form.Item name="type_name" label="类型名" rules={[{ required: true, message: "请输入类型名" }]}>
            <Input placeholder="如 VARCHAR" />
          </Form.Item>
          <Form.Item name="allowed_forms" label="允许形态" tooltip="逗号分隔的允许长度，如 10,50,100,1000">
            <Input placeholder="10,50,100,1000" />
          </Form.Item>
          <Form.Item name="rounding_rule" label="取整规则">
            <Input placeholder="如 长度向上取最近允许值，超出用最大值" />
          </Form.Item>
          <Form.Item name="platform_syntax" label="平台写法（可选）">
            <Input placeholder="与标准 Gauss 不同时填" />
          </Form.Item>
          <Form.Item name="note" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space>
            <Form.Item name="enabled" label="状态"><Select options={ENABLED_OPTIONS} style={{ width: 100 }} /></Form.Item>
            <Form.Item name="sort_order" label="排序"><InputNumber /></Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={funcEditing ? "编辑函数规则" : "新增函数规则"}
        open={funcOpen}
        onCancel={() => setFuncOpen(false)}
        onOk={() => funcForm.submit()}
        destroyOnClose
      >
        <Form form={funcForm} layout="vertical" onFinish={saveFunc}
          initialValues={funcEditing ?? { enabled: 1, sort_order: 0 }}>
          <Form.Item name="function_name" label="函数名" rules={[{ required: true, message: "请输入函数名" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="signature" label="签名" tooltip="如 plat_to_date(text, fmt)">
            <Input placeholder="plat_to_date(text, fmt)" />
          </Form.Item>
          <Form.Item name="description" label="语义说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="example" label="用法示例">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space>
            <Form.Item name="enabled" label="状态"><Select options={ENABLED_OPTIONS} style={{ width: 100 }} /></Form.Item>
            <Form.Item name="sort_order" label="排序"><InputNumber /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
}
