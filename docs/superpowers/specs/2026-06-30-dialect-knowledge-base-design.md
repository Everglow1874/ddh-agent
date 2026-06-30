# 设计：平台方言知识库（类型 + 内部函数）

日期：2026-06-30
分支：`feature/dialect-kb`

## 背景与目标

平台生成的是标准 GaussDB（PostgreSQL 兼容）SQL，但本平台方言与标准存在小差异。
希望让 AI「挂知识库」，按平台方言生成 SQL。本期支持两类知识库：

1. **类型知识库**：标准类型与平台类型的差异，尤其是**允许的长度/精度形态**及取整规则。
   例：`VARCHAR` 仅允许 `VARCHAR(10/50/100/1000)`，所需长度向上取最近允许值。
2. **内部函数知识库**：平台内置函数（函数名、签名、语义、示例），让 AI 用平台函数而非标准 Gauss 函数。

均为**几十条固定规则** → **全量常驻注入**系统提示词，不做检索/向量。

## 已确认的决策

| # | 决策 | 说明 |
|---|------|------|
| 1 | 仅做两类 | 类型知识库 + 内部函数知识库；不做字段库、示例作业库 |
| 2 | 全局共享 | 一套全平台共用的方言规则 |
| 3 | 沿用现状鉴权 | 不加管理员角色校验，像「系统配置」一样登录用户即可维护，放在 admin 区 |
| 4 | 全量注入 | 规则量小，每次生成查库拼接进系统提示词，不做缓存 |
| 5 | 长度约束归类型库 | 「VARCHAR 只允许 10/50/100/1000」属类型知识库 |

## 数据模型

```sql
CREATE TABLE IF NOT EXISTS dialect_type_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY     COMMENT '主键ID',
    type_name       VARCHAR(64) NOT NULL                 COMMENT '类型名，如 VARCHAR',
    allowed_forms   VARCHAR(255)                         COMMENT '允许的长度/精度形态，如 10,50,100,1000',
    rounding_rule   VARCHAR(255)                         COMMENT '取整规则，如 向上取最近允许值',
    platform_syntax VARCHAR(128)                         COMMENT '平台写法（与标准不同时填）',
    note            TEXT                                 COMMENT '说明',
    enabled         SMALLINT DEFAULT 1                   COMMENT '是否启用 0/1',
    sort_order      INT DEFAULT 0                        COMMENT '排序',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方言-类型知识库';

CREATE TABLE IF NOT EXISTS dialect_function_rule (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY      COMMENT '主键ID',
    function_name  VARCHAR(128) NOT NULL                 COMMENT '函数名',
    signature      VARCHAR(255)                          COMMENT '函数签名',
    description    TEXT                                  COMMENT '语义说明',
    example        TEXT                                  COMMENT '用法示例',
    note           TEXT                                  COMMENT '备注',
    enabled        SMALLINT DEFAULT 1                    COMMENT '是否启用 0/1',
    sort_order     INT DEFAULT 0                         COMMENT '排序',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方言-内部函数知识库';
```

> 主 `schema.sql` 与测试 `schema.sql` 均加这两表（H2 测试库用对应 DDL）。新表用
> `CREATE TABLE IF NOT EXISTS`，`DatabaseMigration` 无需改动（仅老库新增表，建表即可）。

## 后端改动（包 `com.ddh.agent`，DDD 四层）

**domain 层**
- `domain/model/dialect/DialectTypeRule.java`、`DialectFunctionRule.java`：`@TableName` 实体。
- `domain/model/dialect/DialectKbRepository.java`：接口
  `listTypeRules()/findTypeById/saveType/deleteType` + 函数版同名方法（含 enabled 过滤的查询）。
- `domain/service/DialectKbDomainService.java`：
  - `buildPromptSection()` → 把**启用**的规则拼成中文 Markdown 段落：
    - 类型：`- VARCHAR：仅允许 VARCHAR(10/50/100/1000)；所需长度向上取最近允许值，超出用最大值。`
      （`allowed_forms` 拼进括号，`rounding_rule`/`note`/`platform_syntax` 追加。）
    - 函数：`- to_date(text, fmt)：<语义>。示例：<example>`
  - 规则为空时返回空串（不注入多余内容）。

**infrastructure 层**
- `persistence/mapper/DialectTypeRuleMapper.java`、`DialectFunctionRuleMapper.java`（BaseMapper）。
- `persistence/repository/DialectKbRepositoryImpl.java`（LambdaQueryWrapper，按 sort_order 排序）。

**application 层**
- `application/service/DialectKbAppService.java`：两类的 list/create/update/delete + DTO 转换。
  （沿用现状：不做管理员角色校验。）

**interfaces 层**
- `interfaces/rest/DialectKbController.java`（`/api/admin/dialect`）：
  - 类型：`GET/POST /types`、`PUT/DELETE /types/{id}`
  - 函数：`GET/POST /functions`、`PUT/DELETE /functions/{id}`
- request/response DTO（snake_case 序列化，与全局一致）。

**AI 注入（关键）** — `domain/service/AgentDomainService.java`
- 注入 `DialectKbDomainService`。
- `buildSystemPrompt(state)`：在 **state=3（规划）和 state=4（生成 SQL）** 的返回末尾追加：
  > 本平台基于 GaussDB 但存在方言差异，生成 SQL 必须遵守以下规则：优先使用平台内置函数；
  > 字段类型只能用允许形态，长度按规则向上取最近允许值。
  >
  > （后接 `buildPromptSection()` 的类型规则 + 内部函数全量清单。）
- 规则段为空时不追加。

## 前端改动（`frontend`）

- `src/pages/admin/DialectKbPage.tsx`：两个 Tab（**类型规则 / 内部函数**），各为表格 + 新增/编辑/删除弹窗。
  - 类型表单：类型名、允许形态、取整规则、平台写法、说明、启用。
  - 函数表单：函数名、签名、语义说明、示例、备注、启用。
- `src/api/dialectKb.ts`：两类 CRUD。
- `src/api/types.ts`：`DialectTypeRule`、`DialectFunctionRule` 类型（snake_case）。
- `src/main.tsx`：加路由 `/admin/dialect`。
- `src/components/AppLayout.tsx`：admin 区导航加「方言知识库」入口（与「系统配置」并列，沿用现状不按角色隐藏）。

## 数据流

```
管理员维护规则（/admin/dialect 增删改）→ dialect_type_rule / dialect_function_rule

用户走对话生成 SQL（state 3/4）
  → AgentDomainService.buildSystemPrompt
  → 追加 DialectKbDomainService.buildPromptSection()（启用规则全量）
  → LLM 按平台方言生成 SQL（用平台函数、合法类型长度）
```

## 错误处理

- 必填校验：类型名 / 函数名为空 → 400。
- 删除/更新不存在的 id → 404（与现有 `deleteColumn` 等一致）。
- 规则全为空 → 提示词不追加方言段，行为回退到当前标准 GaussDB。

## 测试

后端（H2，`src/test`）：
- 两类 CRUD 走通（增→列表含→改→删）。
- `DialectKbDomainService.buildPromptSection()`：含某条类型规则文本（如 `VARCHAR` 与允许形态）
  与某条函数规则文本；启用=0 的不出现。
- 新表在 H2 schema 存在，实体读写正常。
- 验证：`mvn test` 通过。

前端：`npm run build` 通过。

## 不在本次范围（YAGNI）

- 字段知识库、示例作业知识库。
- 按需检索 / 向量化（全量注入即可）。
- 真正执行/校验生成的 SQL。
- 管理员角色强校验（沿用现状，后续若做 RBAC 再统一加）。
