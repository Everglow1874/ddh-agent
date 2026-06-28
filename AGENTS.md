# AGENTS.md

## 架构

- **后端**: `backend-java/` — Spring Boot 2.7.18, Java 8, MyBatis-Plus, MySQL, JWT 认证
- **前端**: `frontend/` — React 18, Ant Design 5, Vite, Vitest
- **技能**: `skills/gaussdb-etl-builder/` — GaussDB ETL 构建器技能

## 快速命令

```powershell
# 后端（需要 MySQL 运行）
cd backend-java
mvn spring-boot:run          # 启动在端口 8000
mvn test                     # 运行测试（H2 内存数据库）

# 前端
cd frontend
npm install                  # 首次安装
npm run dev                  # 启动在端口 3000（代理 /api 到 :8000）
npm test                     # 运行测试
```

## 关键信息

- 后端配置: `backend-java/src/main/resources/application.yml`
- LLM 提供商: Claude, Qwen, DeepSeek — 在 `application.yml` 的 `llm.provider` 中配置
- 生成的 ETL 输出: `backend-java/projects/`（已 gitignore）
- 前端代理 `/api` 到 `http://localhost:8000` — 开发时无需 CORS 配置
- MySQL 字符集必须是 `utf8mb4`

## 重要说明

- `CLAUDE.md` 已过时 — 引用的 Python 后端和 `demo.py` 已不存在
- 项目从 Python 迁移到 Java — 参见 `docs/superpowers/specs/2026-06-13-python-to-java-migration-design.md`
- 需要 Java 8（在 `pom.xml` 中设置）

## 分支约定

- `master` — 稳定分支，仅合并功能代码和设计文档
- `opencode` — opencode 开发分支，包含所有内容
- 合并到 master 时排除 AI 过程文档:
  - `docs/superpowers/plans/`（AI 实施计划）
  - `skills/gaussdb-etl-builder-workspace/`（AI 评估数据）
