import { useState, useEffect } from "react";
import { Card, Button, Modal, Form, Input, message, Row, Col, Tag, Space } from "antd";
import { useNavigate } from "react-router-dom";
import { listProjects, createProject } from "../api/projects";
import type { Project } from "../api/types";

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
  1: { text: "草稿", color: "default" },
  2: { text: "进行中", color: "blue" },
  3: { text: "已完成", color: "green" },
};

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
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

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: "space-between", width: "100%" }}>
        <h2 style={{ margin: 0 }}>我的项目</h2>
        <Button type="primary" onClick={() => setOpen(true)}>+ 新建项目</Button>
      </Space>
      <Row gutter={[16, 16]}>
        {projects.map((p) => (
          <Col span={8} key={p.id}>
            <Card hoverable onClick={() => navigate(`/projects/${p.id}`)} title={p.name}>
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
    </div>
  );
}
