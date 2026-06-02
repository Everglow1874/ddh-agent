import { Form, Input, Button, Card, message, Typography } from "antd";
import { useNavigate, Link } from "react-router-dom";
import { register as apiRegister } from "../api/auth";

export function RegisterPage() {
  const navigate = useNavigate();

  const onFinish = async (values: { username: string; email: string; password: string }) => {
    try {
      await apiRegister(values.username, values.email, values.password);
      message.success("注册成功，请登录");
      navigate("/login");
    } catch {
      message.error("注册失败，用户名或邮箱可能已被占用");
    }
  };

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", background: "#f8faff" }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: "center", color: "#4361ee" }}>注册账号</Typography.Title>
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: "email" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>注册</Button>
        </Form>
        <div style={{ textAlign: "center", marginTop: 12 }}>
          已有账号？<Link to="/login">登录</Link>
        </div>
      </Card>
    </div>
  );
}
