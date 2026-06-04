# DDH Agent · 数仓 ETL 作业辅助开发平台

通过自然语言与 AI Agent 沟通取数需求，自动分析目标表结构、拆分 ETL 步骤，生成 GaussDB 语法的 SQL 文件和执行计划文档。

- **后端**：FastAPI + SQLAlchemy + MySQL
- **前端**：React 18 + Ant Design 5（晴空白主题）
- **AI**：Claude / Qwen / DeepSeek，通过配置文件切换
- 设计文档：[docs/superpowers/specs/2026-05-31-ddh-agent-platform-design.md](docs/superpowers/specs/2026-05-31-ddh-agent-platform-design.md)

---

## 环境要求

| 组件 | 版本 |
|------|------|
| Python | 3.11+ |
| Node.js | 18+（推荐 20/22） |
| MySQL | 8.x |
| LLM | Claude 或 Qwen 的 API Key（跑通 Agent 对话时需要） |

---

## 一、准备 MySQL

确保本地 MySQL 已启动，创建数据库（字符集必须 `utf8mb4`，否则中文乱码）：

```sql
CREATE DATABASE IF NOT EXISTS ddh_agent CHARACTER SET utf8mb4;
```

> 数据表无需手动创建——后端启动时会自动建表。

---

## 二、启动后端

```powershell
cd backend

# 1. 创建并激活虚拟环境（首次）
python -m venv .venv
.venv\Scripts\Activate.ps1

# 2. 安装依赖
pip install -r requirements.txt

# 3. 准备配置文件（从模板复制）
copy config.yaml.example config.yaml
```

编辑 `backend/config.yaml`，填入你的实际配置：

```yaml
app:
  secret_key: "换成一段足够长的随机字符串"      # 生产环境务必修改
  algorithm: "HS256"
  access_token_expire_minutes: 1440

database:
  # 注意结尾的 ?charset=utf8mb4 不可省略
  url: "mysql+pymysql://用户名:密码@localhost:3306/ddh_agent?charset=utf8mb4"

llm:
  provider: claude            # claude | qwen | deepseek
  claude:
    api_key: "sk-ant-..."     # 填入真实 Key 才能跑通 Agent 对话
    model: claude-sonnet-4-6
  qwen:
    api_key: ""
    model: qwen-max
  deepseek:
    api_key: ""
    model: deepseek-chat      # 或 deepseek-v4-flash / deepseek-v4-pro

files:
  projects_dir: "./projects"  # 生成的 SQL / plan.md 存放目录
```

启动服务：

```powershell
uvicorn app.main:app --reload --port 8000
```

启动成功后：
- API 文档（Swagger）：http://127.0.0.1:8000/docs
- 首次启动会自动在 MySQL 中创建全部数据表

---

## 三、启动前端

另开一个终端：

```powershell
cd frontend

# 安装依赖（首次）
npm install

# 启动开发服务器
npm run dev
```

打开浏览器访问 **http://localhost:3000**

> 前端开发服务器已配置代理：所有 `/api` 请求自动转发到后端 `http://localhost:8000`，无需额外配置跨域。

---

## 四、使用流程

1. **注册 / 登录** —— 首次使用先注册账号
2. **原表仓库** —— 导入 CSV 定义原表结构
   - CSV 格式：每行一个字段，列为 `column_name,data_type,comment`
   - 含逗号的类型（如 `DECIMAL(18,2)`）需用双引号包裹
   - 可选「公共表库」（团队共享）或「我的私有表」
3. **新建项目** —— 在「我的项目」创建需求项目
4. **关联原表** —— 进入项目详情，勾选本次 ETL 用到的原表
5. **Agent 对话** —— 点「进入 Agent 对话」，用自然语言描述取数需求
   - Agent 分析需求 → 弹出**目标表结构确认卡片** → 点「确认」
   - Agent 规划步骤 → 弹出**ETL 步骤确认卡片** → 点「确认」
   - Agent 逐步生成 GaussDB SQL，右侧面板实时显示，可下载 ZIP（含 SQL + plan.md）

---

## 五、运行测试

```powershell
# 后端（使用 SQLite 内存库，无需 MySQL）
cd backend
.venv\Scripts\Activate.ps1
pytest

# 前端
cd frontend
npm test
```

---

## CSV 导入示例

`dw_order.csv`：

```csv
column_name,data_type,comment
user_id,VARCHAR(64),用户ID
order_date,DATE,订单日期
amount,"DECIMAL(18,2)",订单金额
```

---

## 常见问题

| 现象 | 原因 / 解决 |
|------|------------|
| 中文显示为乱码 | 连接串缺少 `?charset=utf8mb4`，补上并重启后端 |
| 后端启动报数据库连接错误 | 检查 MySQL 是否启动、`config.yaml` 的用户名密码端口是否正确、`ddh_agent` 库是否已创建 |
| Agent 对话无响应 | `config.yaml` 中对应 provider 的 `api_key` 为空，需填入真实 Key |
| 前端请求 401 跳转登录 | Token 过期或未登录，重新登录即可 |
| `config.yaml` 不在 git 中 | 这是有意的——它含凭据，已被 gitignore；从 `config.yaml.example` 复制生成 |

---

## 项目结构

```
ddh-agent/
├── backend/                 # FastAPI 后端
│   ├── app/
│   │   ├── main.py          # 应用入口（CORS、路由挂载、启动建表）
│   │   ├── config.py        # 配置加载
│   │   ├── database.py      # SQLAlchemy 引擎 / 会话
│   │   ├── deps.py          # 依赖注入（get_db、get_current_user）
│   │   ├── models/          # 数据模型（9 张表）
│   │   ├── schemas/         # Pydantic 请求/响应模型
│   │   ├── routers/         # API 路由（auth/tables/projects/conversations/jobs/admin）
│   │   └── services/        # 业务逻辑
│   │       ├── llm/         # LLM 抽象层（Claude / Qwen）
│   │       ├── agent_service.py   # Agent 编排 + SSE 流
│   │       ├── agent_tools.py     # Agent 工具集
│   │       └── etl_service.py     # SQL / plan.md 文件生成
│   ├── tests/               # pytest 测试
│   ├── config.yaml.example  # 配置模板
│   └── requirements.txt
├── frontend/                # React + Ant Design 前端
│   └── src/
│       ├── api/             # API 客户端 + SSE 读取器
│       ├── auth/            # 认证上下文 + 路由守卫
│       ├── components/      # 通用组件（布局）
│       └── pages/           # 页面（登录/表/项目/对话/配置）
└── docs/superpowers/        # 设计文档与实施计划
```
