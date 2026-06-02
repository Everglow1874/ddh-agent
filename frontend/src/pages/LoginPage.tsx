import { Form, Input, Button, Card, message, Typography } from "antd";
import { useNavigate, Link } from "react-router-dom";
import { login as apiLogin } from "../api/auth";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const onFinish = async (values: { username: string; password: string }) => {
    try {
      const { access_token } = await apiLogin(values.username, values.password);
      login(access_token);
      navigate("/projects");
    } catch {
      message.error("用户名或密码错误");
    }
  };

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", background: "#f8faff" }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: "center", color: "#4361ee" }}>DDH Agent</Typography.Title>
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>登录</Button>
        </Form>
        <div style={{ textAlign: "center", marginTop: 12 }}>
          没有账号？<Link to="/register">注册</Link>
        </div>
      </Card>
    </div>
  );
}
