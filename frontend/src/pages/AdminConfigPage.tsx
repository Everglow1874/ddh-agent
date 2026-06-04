import { useState, useEffect } from "react";
import { Card, Form, Select, Input, Button, message } from "antd";
import { getConfig, updateConfig } from "../api/admin";

export function AdminConfigPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      const cfg = await getConfig();
      form.setFieldsValue(cfg);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onFinish = async (values: { provider: string; model: string }) => {
    setLoading(true);
    try {
      const cfg = await updateConfig(values.provider, values.model);
      form.setFieldsValue(cfg);
      message.success("配置已更新");
    } catch {
      message.error("更新失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="LLM 配置" style={{ maxWidth: 480 }}>
      <Form form={form} onFinish={onFinish} layout="vertical">
        <Form.Item name="provider" label="模型提供方" rules={[{ required: true }]}>
          <Select
            options={[
              { label: "Claude", value: "claude" },
              { label: "Qwen", value: "qwen" },
              { label: "DeepSeek", value: "deepseek" },
            ]}
          />
        </Form.Item>
        <Form.Item name="model" label="模型名称" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>保存</Button>
      </Form>
    </Card>
  );
}
