import { useState, useEffect } from "react";
import { Card, Button, Modal, Form, Input, message, Row, Col, Tag, Space, Select } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { listProjects, createProject, updateProject } from "../api/projects";
import type { Project } from "../api/types";

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
  1: { text: "草稿", color: "default" },
  2: { text: "进行中", color: "blue" },
  3: { text: "已完成", color: "green" },
};

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [open, setOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const navigate = useNavigate();

  const load = async () => setProjects(await listProjects());

  useEffect(() => {
    load();
  }, []);

  const onCreate = async (values: { name: string; description?: string }) => {
    try {
      await createProject(values.name, values.description);
      message.success("项目已创建");
      setOpen(false);
      form.resetFields();
      load();
    } catch {
      message.error("创建失败");
    }
  };

  const openEdit = (p: Project) => {
    setEditing(p);
    editForm.setFieldsValue({ name: p.name, description: p.description, status: p.status });
    setEditOpen(true);
  };

  const onEdit = async (values: { name: string; description?: string; status: number }) => {
    if (!editing) return;
    try {
      await updateProject(editing.id, values);
      message.success("项目已更新");
      setEditOpen(false);
      setEditing(null);
      load();
    } catch {
      message.error("更新失败");
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: "space-between", width: "100%" }}>
        <h2 style={{ margin: 0 }}>我的项目</h2>
        <Button type="primary" onClick={() => setOpen(true)}>+ 新建项目</Button>
      </Space>
      <Row gutter={[16, 16]}>
        {projects.map((p) => (
          <Col span={8} key={p.id}>
            <Card
              hoverable
              onClick={() => navigate(`/projects/${p.id}`)}
              title={p.name}
              extra={
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={(e) => { e.stopPropagation(); openEdit(p); }}
                />
              }
            >
              <p style={{ color: "#8a9ab8" }}>{p.description ?? "无描述"}</p>
              <Tag color={STATUS_LABEL[p.status]?.color}>{STATUS_LABEL[p.status]?.text}</Tag>
            </Card>
          </Col>
        ))}
      </Row>

      <Modal title="新建项目" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()}>
        <Form form={form} onFinish={onCreate} layout="vertical">
          <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="编辑项目" open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => editForm.submit()}>
        <Form form={editForm} onFinish={onEdit} layout="vertical">
          <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>草稿</Select.Option>
              <Select.Option value={2}>进行中</Select.Option>
              <Select.Option value={3}>已完成</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
