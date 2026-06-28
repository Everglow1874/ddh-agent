import { Layout, Menu, Dropdown, Button } from "antd";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PRIMARY } from "../theme";

const { Header, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout } = useAuth();

  const selectedKey = location.pathname.startsWith("/projects")
    ? "/projects"
    : location.pathname.startsWith("/tables")
    ? "/tables"
    : location.pathname.startsWith("/admin")
    ? "/admin/config"
    : "";

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Header style={{ display: "flex", alignItems: "center", background: "#fff", borderBottom: "1px solid #e8eef8" }}>
        <div style={{ color: PRIMARY, fontWeight: 700, fontSize: 18, marginRight: 32 }}>DDH Agent</div>
        <Menu
          mode="horizontal"
          selectedKeys={[selectedKey]}
          style={{ flex: 1, borderBottom: "none" }}
          onClick={(e) => navigate(e.key)}
          items={[
            { key: "/tables", label: "原表仓库" },
            { key: "/projects", label: "我的项目" },
            { key: "/admin/config", label: "系统配置" },
          ]}
        />
        <Dropdown
          menu={{
            items: [
              {
                key: "logout",
                label: "退出登录",
                onClick: () => {
                  logout();
                  navigate("/login");
                },
              },
            ],
          }}
        >
          <Button type="text">账户 ▾</Button>
        </Dropdown>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
